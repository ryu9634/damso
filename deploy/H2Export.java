import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * H2 파일 DB 의 데이터를 PostgreSQL 로 옮기기 위한 내보내기 도구. (1회성)
 *
 * <p>실행 방법 — migrate-h2-to-postgres.sh 가 대신 호출한다.
 * <pre>java -cp h2.jar H2Export.java &lt;jdbc-url&gt; &lt;출력 디렉터리&gt;</pre>
 *
 * <p><b>스키마는 옮기지 않는다.</b> PostgreSQL 쪽 테이블은 애플리케이션을 한 번 띄워
 * Flyway 가 만들게 하고, 여기서는 데이터만 CSV 로 뽑는다. H2 스키마를 그대로 옮기면
 * 이미 제거된 레거시 컬럼(provider, height 등)이 따라와 스키마 보정 코드와 충돌한다.
 *
 * <p>컬럼 순서 문제를 피하려고 CSV 첫 줄에 컬럼명을 넣고, 생성되는 load.sql 의 \copy 에도
 * 컬럼 목록을 명시한다. 두 DB 의 컬럼 순서가 달라도 값이 엉뚱한 칸에 들어가지 않는다.
 *
 * <p>NULL 과 빈 문자열 구분: NULL 은 따옴표 없는 빈 칸으로, 빈 문자열은 {@code ""} 로 쓴다.
 * PostgreSQL 의 {@code WITH (FORMAT csv, NULL '')} 이 이 둘을 정확히 구분해서 읽는다.
 */
public class H2Export {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("사용법: H2Export <jdbc-url> <출력 디렉터리>");
            System.exit(1);
        }
        String url = args[0];
        Path outDir = Path.of(args[1]);
        Files.createDirectories(outDir);

        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            List<String> tables = listTables(conn);
            if (tables.isEmpty()) {
                System.err.println("[ERROR] PUBLIC 스키마에 테이블이 없습니다. JDBC URL 을 확인하세요.");
                System.exit(1);
            }

            Map<String, Integer> counts = new LinkedHashMap<>();
            Map<String, List<String>> columns = new LinkedHashMap<>();

            for (String table : tables) {
                Path csv = outDir.resolve(table + ".csv");
                Export result = exportTable(conn, table, csv);
                counts.put(table, result.rows);
                columns.put(table, result.columns);
                System.out.printf("  %-24s %6d 행%n", table, result.rows);
            }

            writeLoadSql(outDir, columns, counts);
            writeCounts(outDir, counts);

            int total = counts.values().stream().mapToInt(Integer::intValue).sum();
            System.out.printf("%n테이블 %d개 / 총 %d행 → %s%n", tables.size(), total, outDir.toAbsolutePath());
        }
    }

    /**
     * H2 내부 테이블을 빼고 실제 데이터 테이블만 고른다.
     * flyway_schema_history 도 뺀다 — 받는 쪽 DB 는 앱을 띄울 때 자기 이력을 만든다.
     */
    private static List<String> listTables(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<>();
        // DATABASE_TO_LOWER=TRUE 로 만들어진 DB 는 스키마명까지 소문자('public')로 저장된다.
        // 대소문자를 가리지 않도록 UPPER() 로 비교한다.
        String sql = "SELECT table_name FROM information_schema.tables "
                + "WHERE UPPER(table_schema) = 'PUBLIC' AND table_type IN ('BASE TABLE', 'TABLE') "
                + "AND LOWER(table_name) <> 'flyway_schema_history' "
                + "ORDER BY table_name";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) tables.add(rs.getString(1).toLowerCase());
        }
        return tables;
    }

    private record Export(int rows, List<String> columns) {}

    private static Export exportTable(Connection conn, String table, Path csv) throws Exception {
        List<String> cols = new ArrayList<>();
        int rows = 0;

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM \"" + table + "\"");
             PrintWriter out = new PrintWriter(Files.newBufferedWriter(csv, StandardCharsets.UTF_8))) {

            ResultSetMetaData meta = rs.getMetaData();
            int n = meta.getColumnCount();

            for (int i = 1; i <= n; i++) {
                cols.add(meta.getColumnLabel(i).toLowerCase());
                // 바이너리 컬럼은 문자열로 옮길 수 없다. 조용히 깨지느니 여기서 멈춘다.
                int type = meta.getColumnType(i);
                if (type == Types.BLOB || type == Types.BINARY || type == Types.VARBINARY
                        || type == Types.LONGVARBINARY) {
                    throw new IllegalStateException(
                            "바이너리 컬럼은 이 도구로 옮길 수 없습니다: " + table + "." + cols.get(i - 1));
                }
            }
            out.println(String.join(",", cols));

            while (rs.next()) {
                StringBuilder line = new StringBuilder();
                for (int i = 1; i <= n; i++) {
                    if (i > 1) line.append(',');
                    String v = rs.getString(i);
                    if (rs.wasNull() || v == null) continue;              // NULL → 빈 칸
                    line.append('"').append(v.replace("\"", "\"\"")).append('"');
                }
                out.println(line);
                rows++;
            }
        }
        return new Export(rows, cols);
    }

    /** PostgreSQL 에서 그대로 실행할 적재 스크립트를 만든다. */
    private static void writeLoadSql(Path outDir, Map<String, List<String>> columns,
                                     Map<String, Integer> counts) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("-- H2Export 가 생성한 적재 스크립트 (자동 생성 — 직접 수정하지 말 것)\n")
          .append("-- 실행:  sudo -u postgres psql -d damso -v ON_ERROR_STOP=1 -f load.sql\n")
          .append("--\n")
          .append("-- superuser 로 실행해야 한다. 외래키 순서를 신경 쓰지 않고 넣기 위해\n")
          .append("-- session_replication_role 로 제약 검사를 잠시 끄기 때문이다.\n\n")
          .append("BEGIN;\n")
          .append("SET session_replication_role = replica;\n\n");

        // 비우는 것은 반드시 한 번에, 적재보다 먼저 한다.
        // 테이블마다 TRUNCATE ... CASCADE 를 따로 돌리면 나중 테이블의 CASCADE 가
        // 앞에서 이미 넣어 둔 자식 테이블 데이터를 도로 지워 버린다.
        sb.append("-- 대상 테이블을 모두 비운다 (여러 번 실행해도 결과가 같도록)\n")
          .append("TRUNCATE TABLE ")
          .append(String.join(", ", columns.keySet().stream().map(t -> "\"" + t + "\"").toList()))
          .append(" CASCADE;\n\n");

        columns.forEach((table, cols) -> {
            if (counts.get(table) == 0) {
                sb.append("-- ").append(table).append(": 빈 테이블, 건너뜀\n\n");
                return;
            }
            String colList = String.join(", ", cols.stream().map(c -> "\"" + c + "\"").toList());
            sb.append("\\copy \"").append(table).append("\" (").append(colList)
              .append(") FROM '").append(table).append(".csv'")
              .append(" WITH (FORMAT csv, HEADER true, NULL '')\n\n");
        });

        sb.append("SET session_replication_role = DEFAULT;\n\n")
          .append("""
                  -- IDENTITY 시퀀스 재설정.
                  -- id 를 명시해서 넣었으므로 시퀀스는 여전히 1을 가리킨다. 이대로 두면
                  -- 다음 INSERT 가 중복 키로 실패한다. 각 테이블의 MAX(id) 로 맞춰 준다.
                  DO $$
                  DECLARE r RECORD; seq TEXT; mx BIGINT;
                  BEGIN
                    FOR r IN
                      SELECT c.table_name, c.column_name
                      FROM information_schema.columns c
                      JOIN information_schema.tables t
                        ON t.table_schema = c.table_schema AND t.table_name = c.table_name
                      WHERE c.table_schema = 'public'
                        AND t.table_type = 'BASE TABLE'
                        AND c.is_identity = 'YES'
                    LOOP
                      seq := pg_get_serial_sequence(format('public.%I', r.table_name), r.column_name);
                      CONTINUE WHEN seq IS NULL;
                      EXECUTE format('SELECT COALESCE(MAX(%I), 0) FROM public.%I', r.column_name, r.table_name)
                        INTO mx;
                      PERFORM setval(seq, GREATEST(mx, 1), mx > 0);
                      RAISE NOTICE '시퀀스 재설정 % → %', seq, mx;
                    END LOOP;
                  END $$;

                  COMMIT;
                  """);

        // 옮긴 행 수 확인. 통계 추정치(pg_stat_user_tables)가 아니라 실제 COUNT 로 센다.
        sb.append("\n-- 적재 결과. counts.txt 의 H2 쪽 수치와 한 줄씩 대조한다.\n");
        List<String> selects = counts.keySet().stream()
                .map(t -> "SELECT '" + t + "' AS table_name, COUNT(*) AS rows FROM \"" + t + "\"")
                .toList();
        sb.append(String.join("\nUNION ALL ", selects)).append("\nORDER BY table_name;\n");

        Files.writeString(outDir.resolve("load.sql"), sb.toString(), StandardCharsets.UTF_8);
    }

    private static void writeCounts(Path outDir, Map<String, Integer> counts) throws IOException {
        StringBuilder sb = new StringBuilder("# H2 원본 행 수 (이전 후 대조용)\n");
        counts.forEach((t, c) -> sb.append(String.format("%-24s %6d%n", t, c)));
        Files.writeString(outDir.resolve("counts.txt"), sb.toString(), StandardCharsets.UTF_8);
    }
}