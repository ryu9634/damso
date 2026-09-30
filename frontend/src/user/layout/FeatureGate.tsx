import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useAppFeatures } from '../features/home/useAppFeatures';
import PageHeading, { PageBody } from './PageHeading';

/**
 * 관리자가 꺼둔 기능의 화면을 막는다.
 *  - 하단 탭에서 감춰도 예전 링크·QR 로 직접 들어올 수 있어 화면에서도 한 번 더 확인한다.
 *  - 안내 문구는 관리자가 환경설정에서 정한 값을 그대로 보여준다.
 */
export default function FeatureGate({
  feature,
  title,
  children,
}: {
  feature: 'matching' | 'selecting';
  title: string;
  children: ReactNode;
}) {
  const { features, loading } = useAppFeatures();

  if (loading) {
    return (
      <PageBody>
        <p className="py-10 text-center text-sm text-stone-400">불러오는 중...</p>
      </PageBody>
    );
  }

  if (features[feature].enabled) return <>{children}</>;

  return (
    <>
      <PageHeading title={title} />
      <PageBody>
        <div className="rounded-3xl bg-white border border-stone-200 shadow-sm p-8 text-center">
          <div className="text-3xl">🌙</div>
          <p className="mt-3 text-sm text-stone-500 whitespace-pre-line leading-relaxed">
            {features[feature].notice || '지금은 이용할 수 없어요.'}
          </p>
          <Link
            to="/"
            className="mt-6 block w-full rounded-full bg-damso-500 text-white py-3 font-semibold active:bg-damso-600"
          >
            홈으로 가기
          </Link>
        </div>
      </PageBody>
    </>
  );
}
