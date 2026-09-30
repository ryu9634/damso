import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PostgreSQL 에서 뽑은 CSV 를 H2 파일 DB 로 적재하는 도구. (집 PC 이전 1회성)
 *
 * <p>실행 방법 — migrate-postgres-to-h2.sh 가 대신 호출한다.
 * <pre>java -cp h2.jar H2Import.java &lt;jdbc-url&gt; &lt;CSV 디렉터리&gt;</pre>
 *
 * <p>{@link H2Export} 의 역방향이고, 같은 원칙을 지킨다.
 *
 * <p><b>스키마는 옮기지 않는다.</b> H2 쪽 테이블은 앱을 local 프로파일로 한 번 띄워
 * Flyway 가 만들게 하고, 여기서는 데이터만 넣는다. PostgreSQL 스키마를 그대로 가져오면
 * 운영에서 보정한 컬럼 타입·제약이 따라와 스키마 보정 코드(SchemaOps)와 충돌한다.
 *
 * <p>컬럼은 이름으로 맞춘다. CSV 첫 줄의 컬럼명과 H2 테이블의 컬럼명을 대조해 교집합만 넣으므로,
 * 두 DB 의 컬럼 순서가 달라도 값이 엉뚱한 칸에 들어가지 않는다. 한쪽에만 있는 컬럼은 경고로 남긴다.
 *
 * <p>PostgreSQL 의 CSV 출력을 그대로 읽기 위해 두 가지를 처리한다.
 * <ul>
 *   <li>NULL 과 빈 문자열: 따옴표 없는 빈 칸은 NULL, {@code ""} 는 빈 문자열.
 *       (내보낼 때 {@code WITH (FORMAT csv, NULL '')} 로 이렇게 구분해 뒀다)</li>
 *   <li>boolean: PostgreSQL 은 {@code t}/{@code f} 로 쓴다. H2 는 이 표기를 모른다.</li>
 * </ul>
 */
public class H2Import {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("사용법: H2Import <jdbc-url> <CSV 디렉터리>");
            System.exit(1);
        }
        String url = args[0];
        Path csvDir = Path.of(args[1]);

        List<Path> csvFiles;
        try (var stream = Files.list(csvDir)) {
            csvFiles = stream.filter(p -> p.getFileName().toString().endsWith(".csv"))
                    .sorted()
                    .toList();
        }
        if (csvFiles.isEmpty()) {
            System.err.println("[ERROR] CSV 가 없습니다: " + csvDir.toAbsolutePath());
            System.exit(1);
        }

        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            conn.setAutoCommit(false);

            // 외래키 순서를 신경 쓰지 않고 넣기 위해 제약 검사를 잠시 끈다.
            // 끈 상태로 끝나면 이후 앱이 제약 없이 돌게 되므로, 실패해도 반드시 되돌린다.
            exec(conn, "SET REFERENTIAL_INTEGRITY FALSE");
            try {
                Map<String, Integer> loaded = new LinkedHashMap<>();
                List<String> warnings = new ArrayList<>();

                // 비우는 것은 적재보다 먼저, 한 번에 끝낸다. 테이블마다 비우고 넣기를 번갈아 하면
                // 뒤에 오는 테이블을 비울 때 앞에서 넣어 둔 자식 행이 함께 날아갈 수 있다.
                List<String> targets = new ArrayList<>();
                for (Path csv : csvFiles) {
                    String table = stripExtension(csv.getFileName().toString());
                    // 옛 번들에 섞여 있어도 건드리지 않는다. 덮어쓰면 이 DB 의 마이그레이션 이력이 원본 것으로 바뀐다.
                    if (table.equalsIgnoreCase("flyway_schema_history")) continue;
                    if (tableExists(conn, table)) {
                        targets.add(table);
                    } else {
                        warnings.add(table + ": H2 에 같은 이름의 테이블이 없어 건너뜁니다 "
                                + "(앱을 local 프로파일로 한 번 띄워 스키마를 만들었는지 확인하세요)");
                    }
                }
                if (targets.isEmpty()) {
                    throw new IllegalStateException(
                            "적재할 테이블이 하나도 없습니다. 앱을 local 프로파일로 먼저 한 번 띄우세요.");
                }
                for (String table : targets) {
                    // TRUNCATE 대신 DELETE — H2 는 참조된 테이블의 TRUNCATE 를 거부하는 경우가 있다.
                    exec(conn, "DELETE FROM \"" + table + "\"");
                }

                for (String table : targets) {
                    int rows = importTable(conn, table, csvDir.resolve(table + ".csv"), warnings);
                    loaded.put(table, rows);
                    System.out.printf("  %-24s %6d 행%n", table, rows);
                }

                resetIdentities(conn, targets);
                conn.commit();

                if (!warnings.isEmpty()) {
                    System.out.println();
                    System.out.println("── 확인이 필요한 항목 ──");
                    warnings.forEach(w -> System.out.println("  [경고] " + w));
                }

                int total = loaded.values().stream().mapToInt(Integer::intValue).sum();
                System.out.printf("%n테이블 %d개 / 총 %d행 적재%n", loaded.size(), total);
                writeCounts(csvDir, loaded);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                exec(conn, "SET REFERENTIAL_INTEGRITY TRUE");
                conn.commit();
            }
        }
    }

    // ── 적재 ──────────────────────────────────────────────

    private static int importTable(Connection conn, String table, Path csv, List<String> warnings)
            throws Exception {

        Map<String, Integer> targetTypes = columnTypes(conn, table);

        try (Reader reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            CsvReader csvReader = new CsvReader(reader);

            List<Field> header = csvReader.nextRow();
            if (header == null) return 0;                      // 헤더조차 없는 빈 파일

            List<String> csvCols = header.stream().map(f -> f.value().toLowerCase(Locale.ROOT)).toList();

            // 이름으로 맞춘다. 양쪽에 다 있는 컬럼만 넣는다.
            List<Integer> useIdx = new ArrayList<>();
            List<String> useCols = new ArrayList<>();
            for (int i = 0; i < csvCols.size(); i++) {
                String c = csvCols.get(i);
                if (targetTypes.containsKey(c)) {
                    useIdx.add(i);
                    useCols.add(c);
                } else {
                    warnings.add(table + "." + c + ": H2 에 없는 컬럼이라 버립니다 "
                            + "(운영에서 이미 제거된 레거시 컬럼이면 정상)");
                }
            }
            for (String c : targetTypes.keySet()) {
                if (!useCols.contains(c)) {
                    warnings.add(table + "." + c + ": CSV 에 없어 비워 둡니다 (NOT NULL 이면 적재가 실패합니다)");
                }
            }
            if (useCols.isEmpty()) {
                warnings.add(table + ": 맞는 컬럼이 없어 건너뜁니다");
                return 0;
            }

            String sql = "INSERT INTO \"" + table + "\" ("
                    + String.join(", ", useCols.stream().map(c -> "\"" + c + "\"").toList())
                    + ") VALUES (" + "?, ".repeat(useCols.size() - 1) + "?)";

            int rows = 0;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                List<Field> row;
                int lineNo = 1;
                while ((row = csvReader.nextRow()) != null) {
                    lineNo++;
                    // 마지막 줄바꿈 때문에 생기는 빈 줄은 넘긴다.
                    if (row.size() == 1 && row.get(0).value().isEmpty() && !row.get(0).quoted()) continue;
                    if (row.size() != csvCols.size()) {
                        throw new IllegalStateException(table + ".csv " + lineNo + "번째 줄: 칸 수가 "
                                + csvCols.size() + "개여야 하는데 " + row.size() + "개입니다.");
                    }
                    for (int k = 0; k < useIdx.size(); k++) {
                        Field f = row.get(useIdx.get(k));
                        bind(ps, k + 1, f, targetTypes.get(useCols.get(k)), table, useCols.get(k), lineNo);
                    }
                    ps.addBatch();
                    rows++;
                    if (rows % 500 == 0) ps.executeBatch();
                }
                ps.executeBatch();
            }
            return rows;
        }
    }

    /** CSV 한 칸을 JDBC 파라미터로 넣는다. */
    private static void bind(PreparedStatement ps, int pos, Field f, int sqlType,
                             String table, String column, int lineNo) throws SQLException {
        // 따옴표 없는 빈 칸 = NULL. 따옴표로 감싼 빈 칸("")은 빈 문자열이다.
        if (f.value().isEmpty() && !f.quoted()) {
            ps.setNull(pos, sqlType);
            return;
        }
        String v = f.value();

        if (sqlType == Types.BOOLEAN || sqlType == Types.BIT) {
            // PostgreSQL 의 CSV 출력은 t / f 다. H2 는 이 표기를 모른다.
            switch (v.toLowerCase(Locale.ROOT)) {
                case "t", "true", "1", "y", "yes" -> ps.setBoolean(pos, true);
                case "f", "false", "0", "n", "no" -> ps.setBoolean(pos, false);
                default -> throw new SQLException(table + "." + column + " " + lineNo
                        + "번째 줄: boolean 으로 읽을 수 없는 값 '" + v + "'");
            }
            return;
        }
        try {
            // 나머지는 대상 컬럼 타입을 알려 주고 H2 가 변환하게 한다
            // (숫자·timestamp·uuid 모두 문자열에서 바로 읽는다).
            ps.setObject(pos, v, sqlType);
        } catch (SQLException first) {
            // 타입을 지정한 변환이 막히면 문자열 그대로 넘겨 컬럼 타입에 맞춘 캐스팅을 맡긴다.
            try {
                ps.setObject(pos, v);
                return;
            } catch (SQLException ignored) {
                throw new SQLException(table + "." + column + " " + lineNo + "번째 줄: '" + v
                        + "' 를 넣을 수 없습니다 — " + first.getMessage(), first);
            }
        } catch (RuntimeException e) {
            throw new SQLException(table + "." + column + " " + lineNo + "번째 줄: '" + v
                    + "' 를 넣을 수 없습니다 — " + e.getMessage(), e);
        }
    }

    /**
     * IDENTITY 시퀀스를 MAX(id) 다음으로 맞춘다.
     *
     * <p>id 를 명시해서 넣었으므로 시퀀스는 여전히 1을 가리킨다. 이대로 두면 앱의 첫 INSERT 가
     * 중복 키로 실패한다 — 이전 직후 "신청이 안 된다"로 드러나는 가장 흔한 실수다.
     */
    private static void resetIdentities(Connection conn, List<String> tables) throws SQLException {
        for (String table : tables) {
            List<String> idCols = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT column_name FROM information_schema.columns "
                            + "WHERE UPPER(table_schema) = 'PUBLIC' AND UPPER(table_name) = ? "
                            + "AND is_identity = 'YES'")) {
                ps.setString(1, table.toUpperCase(Locale.ROOT));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) idCols.add(rs.getString(1).toLowerCase(Locale.ROOT));
                }
            }
            for (String col : idCols) {
                long max = 0;
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery(
                             "SELECT COALESCE(MAX(\"" + col + "\"), 0) FROM \"" + table + "\"")) {
                    if (rs.next()) max = rs.getLong(1);
                }
                exec(conn, "ALTER TABLE \"" + table + "\" ALTER COLUMN \"" + col
                        + "\" RESTART WITH " + (max + 1));
                System.out.printf("  시퀀스 재설정 %s.%s → 다음 %d%n", table, col, max + 1);
            }
        }
    }

    // ── H2 메타데이터 ─────────────────────────────────────

    /** 테이블의 컬럼명(소문자) → java.sql.Types. 순서는 테이블 정의 순서. */
    private static Map<String, Integer> columnTypes(Connection conn, String table) throws SQLException {
        Map<String, Integer> types = new LinkedHashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM \"" + table + "\" WHERE 1 = 0")) {
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                int type = meta.getColumnType(i);
                String name = meta.getColumnLabel(i).toLowerCase(Locale.ROOT);
                // 바이너리 컬럼은 CSV 로 오지 않는다 (H2Export 도 같은 이유로 거부한다).
                // 단 UUID 는 예외다 — H2 가 이것도 BINARY 로 보고하지만 CSV 에는
                // 사람이 읽는 문자열로 들어 있고, 아래 bind() 가 문자열에서 되살린다.
                if (type == Types.BLOB || type == Types.BINARY || type == Types.VARBINARY
                        || type == Types.LONGVARBINARY) {
                    if (!"UUID".equalsIgnoreCase(meta.getColumnTypeName(i))) {
                        throw new IllegalStateException(
                                "바이너리 컬럼은 이 도구로 옮길 수 없습니다: " + table + "." + name);
                    }
                    type = Types.OTHER;
                }
                types.put(name, type);
            }
        }
        return types;
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE UPPER(table_schema) = 'PUBLIC' AND UPPER(table_name) = ? "
                        + "AND table_type IN ('BASE TABLE', 'TABLE')")) {
            ps.setString(1, table.toUpperCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static void exec(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return (dot < 0 ? name : name.substring(0, dot)).toLowerCase(Locale.ROOT);
    }

    private static void writeCounts(Path dir, Map<String, Integer> counts) throws IOException {
        StringBuilder sb = new StringBuilder("# H2 적재 행 수 (counts.txt 의 PostgreSQL 수치와 대조)\n");
        counts.forEach((t, c) -> sb.append(String.format("%-24s %6d%n", t, c)));
        Files.writeString(dir.resolve("counts-h2.txt"), sb.toString(), StandardCharsets.UTF_8);
    }

    // ── CSV 읽기 ──────────────────────────────────────────

    /** 한 칸의 값과, 그 값이 따옴표로 감싸여 있었는지. 따옴표 유무가 NULL 과 빈 문자열을 가른다. */
    private record Field(String value, boolean quoted) {}

    /**
     * RFC 4180 방식의 CSV 리더.
     *
     * <p>직접 쓴 이유는 값 안의 줄바꿈이다. 소개 글처럼 여러 줄인 값은 따옴표 안에 줄바꿈을 품고
     * 저장되므로, 파일을 줄 단위로 끊어 읽으면 한 행이 여러 조각으로 깨진다.
     */
    private static final class CsvReader {
        private final Reader in;

        CsvReader(Reader in) { this.in = in; }

        /** 다음 한 행. 파일이 끝나면 null. */
        List<Field> nextRow() throws IOException {
            int c = in.read();
            if (c == -1) return null;

            List<Field> row = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean quoted = false;
            boolean inQuotes = false;

            while (true) {
                if (c == -1) {
                    row.add(new Field(cur.toString(), quoted));
                    return row;
                }
                if (inQuotes) {
                    if (c == '"') {
                        int next = in.read();
                        if (next == '"') {                     // "" → 값 안의 따옴표 한 개
                            cur.append('"');
                        } else {
                            inQuotes = false;
                            c = next;
                            continue;
                        }
                    } else {
                        cur.append((char) c);
                    }
                } else if (c == '"') {
                    quoted = true;
                    inQuotes = true;
                } else if (c == ',') {
                    row.add(new Field(cur.toString(), quoted));
                    cur.setLength(0);
                    quoted = false;
                } else if (c == '\n') {
                    row.add(new Field(cur.toString(), quoted));
                    return row;
                } else if (c == '\r') {
                    // CRLF 의 CR 은 버린다. 값 안의 CR 은 따옴표 안에 있어 여기까지 오지 않는다.
                } else {
                    cur.append((char) c);
                }
                c = in.read();
            }
        }
    }
}
