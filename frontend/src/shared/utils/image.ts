/**
 * 업로드 전 이미지 압축.
 *
 * 모바일 사진첩 사진은 원본이 3~12MB라 그대로 올리면
 *  - LTE 환경에서 업로드가 오래 걸리고
 *  - 서버 업로드 제한(8MB)에 걸리며
 *  - 아이폰 HEIC 는 관리자 PC(윈도우 크롬)에서 열리지 않는다.
 *
 * 캔버스로 다시 그려 JPEG 로 내보내면 세 문제가 한 번에 해결된다.
 * (HEIC 도 디코딩만 되면 JPEG 로 바뀐다 — iOS Safari 는 네이티브로 디코딩한다)
 *
 * 디코딩에 실패하면 원본을 그대로 돌려준다. 압축은 어디까지나 최적화이므로
 * 실패했다고 등록을 막지는 않는다.
 */

export interface CompressOptions {
  /** 긴 변 최대 픽셀. */
  maxEdge?: number;
  /** JPEG 품질 (0~1). */
  quality?: number;
  /** 이 크기를 넘으면 품질을 낮춰 재시도한다. */
  targetBytes?: number;
}

const DEFAULTS: Required<CompressOptions> = {
  maxEdge: 1600,
  quality: 0.82,
  targetBytes: 1_500_000, // 1.5MB
};

/** 이미지를 디코딩한다. EXIF 회전 정보를 반영한다. */
async function decode(file: File): Promise<ImageBitmap | HTMLImageElement> {
  if (typeof createImageBitmap === 'function') {
    try {
      // imageOrientation: 세로로 찍은 사진이 눕지 않도록 EXIF 회전을 적용
      return await createImageBitmap(file, { imageOrientation: 'from-image' });
    } catch {
      // 일부 브라우저는 옵션을 거부한다 — 옵션 없이 재시도
      try {
        return await createImageBitmap(file);
      } catch {
        /* 아래 <img> 방식으로 폴백 */
      }
    }
  }

  const url = URL.createObjectURL(file);
  try {
    return await new Promise<HTMLImageElement>((resolve, reject) => {
      const img = new Image();
      img.onload = () => resolve(img);
      img.onerror = () => reject(new Error('이미지를 읽을 수 없습니다.'));
      img.src = url;
    });
  } finally {
    // onload 이후 해제해도 이미 디코딩된 이미지는 유지된다
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}

function sizeOf(src: ImageBitmap | HTMLImageElement): { w: number; h: number } {
  return src instanceof HTMLImageElement
    ? { w: src.naturalWidth, h: src.naturalHeight }
    : { w: src.width, h: src.height };
}

function toBlob(canvas: HTMLCanvasElement, quality: number): Promise<Blob | null> {
  return new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality));
}

/** 확장자를 .jpg 로 바꾼 파일명. */
function jpgName(name: string): string {
  const base = name.replace(/\.[^.]+$/, '') || 'photo';
  return `${base}.jpg`;
}

export async function compressImage(file: File, opts: CompressOptions = {}): Promise<File> {
  const { maxEdge, quality, targetBytes } = { ...DEFAULTS, ...opts };

  // 애니메이션 GIF 는 캔버스로 다시 그리면 첫 프레임만 남는다 — 건드리지 않는다
  if (file.type === 'image/gif') return file;

  let src: ImageBitmap | HTMLImageElement;
  try {
    src = await decode(file);
  } catch {
    return file; // 디코딩 실패(예: 데스크톱 브라우저의 HEIC) — 원본 그대로
  }

  try {
    const { w, h } = sizeOf(src);
    if (!w || !h) return file;

    const scale = Math.min(1, maxEdge / Math.max(w, h));
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(w * scale));
    canvas.height = Math.max(1, Math.round(h * scale));

    const ctx = canvas.getContext('2d');
    if (!ctx) return file;
    ctx.drawImage(src, 0, 0, canvas.width, canvas.height);

    // 목표 용량을 넘으면 품질을 단계적으로 낮춘다
    let blob: Blob | null = null;
    for (const q of [quality, 0.7, 0.6, 0.5]) {
      blob = await toBlob(canvas, q);
      if (blob && blob.size <= targetBytes) break;
    }
    if (!blob) return file;

    // 압축했는데 원본보다 커졌다면(이미 잘 압축된 작은 사진) 원본을 쓴다
    if (blob.size >= file.size && file.type === 'image/jpeg') return file;

    return new File([blob], jpgName(file.name), {
      type: 'image/jpeg',
      lastModified: file.lastModified,
    });
  } catch {
    return file;
  } finally {
    if (!(src instanceof HTMLImageElement)) src.close?.();
  }
}

/** 사람이 읽는 파일 크기. */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes}B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)}KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)}MB`;
}