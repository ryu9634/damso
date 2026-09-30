import type {
  ButtonHTMLAttributes,
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from 'react';

/**
 * 관리자 화면 공통 UI 컴포넌트.
 * 모든 관리자 페이지가 동일한 헤더/카드/버튼 스타일을 쓰도록 통일한다.
 */

// ─── 페이지 헤더 ──────────────────────────────────────────
export function PageHeader({
  title, description, actions,
}: {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <header className="flex flex-wrap items-start justify-between gap-3 border-b border-stone-200 pb-4">
      <div className="min-w-0">
        <h1 className="text-2xl font-bold text-stone-900">{title}</h1>
        {description && <p className="text-sm text-stone-500 mt-1">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </header>
  );
}

// ─── 카드 / 섹션 ──────────────────────────────────────────
export function Card({
  title, actions, children, className = '',
}: {
  title?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`bg-white border border-stone-200 rounded-xl shadow-sm ${className}`}>
      {(title || actions) && (
        <div className="flex items-center justify-between gap-2 px-5 py-3 border-b border-stone-100">
          {title && <div className="text-sm font-semibold text-stone-700">{title}</div>}
          {actions && <div className="flex items-center gap-2">{actions}</div>}
        </div>
      )}
      <div className="p-5">{children}</div>
    </section>
  );
}

// ─── 버튼 ─────────────────────────────────────────────────
type Variant = 'primary' | 'secondary' | 'danger' | 'ghost';

const VARIANT: Record<Variant, string> = {
  primary: 'bg-damso-500 text-white hover:bg-damso-600 disabled:bg-stone-300',
  secondary: 'border border-stone-300 text-stone-700 bg-white hover:bg-stone-100 disabled:opacity-50',
  danger: 'border border-red-300 text-red-700 bg-white hover:bg-red-50 disabled:opacity-50',
  ghost: 'text-stone-500 hover:text-stone-800 hover:bg-stone-100 disabled:opacity-50',
};

export function Button({
  variant = 'secondary', size = 'md', className = '', children, ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; size?: 'sm' | 'md' }) {
  const sizeCls = size === 'sm' ? 'px-3 py-1.5 text-xs' : 'px-4 py-2 text-sm';
  return (
    <button
      {...rest}
      className={`rounded-md font-semibold transition ${sizeCls} ${VARIANT[variant]} ${className}`}
    >
      {children}
    </button>
  );
}

// ─── 통계/요약 카드 ───────────────────────────────────────
export function StatCard({
  label, value, sub, accent = 'text-stone-900',
}: {
  label: string;
  value: ReactNode;
  sub?: ReactNode;
  accent?: string;
}) {
  return (
    <div className="bg-white border border-stone-200 rounded-xl shadow-sm p-5">
      <div className="text-xs font-medium text-stone-500">{label}</div>
      <div className={`text-3xl font-bold mt-1 ${accent}`}>{value}</div>
      {sub && <div className="text-xs text-stone-400 mt-1">{sub}</div>}
    </div>
  );
}

// ─── 빈 상태 ──────────────────────────────────────────────
export function Empty({ children = '데이터가 없습니다.' }: { children?: ReactNode }) {
  return <div className="text-sm text-stone-400 py-10 text-center">{children}</div>;
}

// ─── 배지 ─────────────────────────────────────────────────
export function Badge({
  children, className = 'bg-stone-100 text-stone-600',
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold whitespace-nowrap ${className}`}
    >
      {children}
    </span>
  );
}

// ─── 폼 입력 ──────────────────────────────────────────────
const FIELD_CLS =
  'w-full border border-stone-300 rounded-md px-3 py-2 text-sm text-stone-800 ' +
  'focus:outline-none focus:ring-2 focus:ring-damso-300 focus:border-damso-400 disabled:bg-stone-100';

export function Input(props: InputHTMLAttributes<HTMLInputElement>) {
  const { className = '', ...rest } = props;
  return <input {...rest} className={`${FIELD_CLS} ${className}`} />;
}

export function Select(props: SelectHTMLAttributes<HTMLSelectElement>) {
  const { className = '', ...rest } = props;
  return <select {...rest} className={`${FIELD_CLS} bg-white ${className}`} />;
}

export function Textarea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const { className = '', ...rest } = props;
  return <textarea {...rest} className={`${FIELD_CLS} resize-y ${className}`} />;
}

/** 라벨 + 입력 묶음. */
export function Field({
  label, hint, children, className = '',
}: {
  label: ReactNode;
  hint?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <label className={`block ${className}`}>
      <span className="block text-xs font-semibold text-stone-500 mb-1">{label}</span>
      {children}
      {hint && <span className="block text-[11px] text-stone-400 mt-1">{hint}</span>}
    </label>
  );
}

// ─── 모달 ─────────────────────────────────────────────────
export function Modal({
  open, title, onClose, children, footer, width = 'max-w-2xl',
}: {
  open: boolean;
  title: ReactNode;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  width?: string;
}) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center p-4 sm:p-8 overflow-y-auto">
      <div className="fixed inset-0 bg-stone-900/40" onClick={onClose} />
      <div className={`relative w-full ${width} bg-white rounded-xl shadow-xl my-4`}>
        <div className="flex items-center justify-between gap-3 px-5 py-3 border-b border-stone-100">
          <div className="text-sm font-bold text-stone-800">{title}</div>
          <button
            onClick={onClose}
            className="text-stone-400 hover:text-stone-700 text-lg leading-none px-1"
            aria-label="닫기"
          >
            ×
          </button>
        </div>
        <div className="p-5">{children}</div>
        {footer && (
          <div className="flex justify-end gap-2 px-5 py-3 border-t border-stone-100">{footer}</div>
        )}
      </div>
    </div>
  );
}

// ─── 우측 슬라이드 패널 ───────────────────────────────────
export function SlideOver({
  open, title, subtitle, onClose, children, footer,
}: {
  open: boolean;
  title: ReactNode;
  subtitle?: ReactNode;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
}) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-40">
      <div className="absolute inset-0 bg-stone-900/30" onClick={onClose} />
      <aside className="absolute right-0 top-0 h-full w-full sm:w-[30rem] bg-white shadow-2xl flex flex-col">
        <div className="flex items-start justify-between gap-3 px-5 py-4 border-b border-stone-200">
          <div className="min-w-0">
            <div className="text-base font-bold text-stone-900 truncate">{title}</div>
            {subtitle && <div className="text-xs text-stone-500 mt-0.5">{subtitle}</div>}
          </div>
          <button
            onClick={onClose}
            className="text-stone-400 hover:text-stone-700 text-xl leading-none px-1"
            aria-label="닫기"
          >
            ×
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-5 space-y-5">{children}</div>
        {footer && (
          <div className="flex flex-wrap justify-end gap-2 px-5 py-3 border-t border-stone-200 bg-stone-50">
            {footer}
          </div>
        )}
      </aside>
    </div>
  );
}
