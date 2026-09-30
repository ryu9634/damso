import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { AppFeatures } from '../../../shared/types';
import { DEFAULT_FEATURES } from '../../../shared/types';
import { appApi } from '../../api/app';

/**
 * 담소 소개팅 · 담소 셀렉팅을 지금 쓸 수 있는지 앱 전체에서 공유한다.
 *  - 관리자가 끄면 하단 탭에서 감추고, 화면에는 안내 문구를 대신 보여준다.
 *  - 못 불러왔을 때는 기본값(둘 다 사용)으로 두고 서버 가드에 맡긴다.
 */
interface FeatureValue {
  features: AppFeatures;
  loading: boolean;
  reload: () => Promise<void>;
}

const Ctx = createContext<FeatureValue | null>(null);

export function AppFeatureProvider({ children }: { children: ReactNode }) {
  const [features, setFeatures] = useState<AppFeatures>(DEFAULT_FEATURES);
  const [loading, setLoading] = useState(true);

  const reload = useCallback(async () => {
    try {
      setFeatures(await appApi.features());
    } catch {
      // 못 불러오면 기본값 그대로 — 실제 차단은 서버가 한다.
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { reload(); }, [reload]);

  const value = useMemo<FeatureValue>(
    () => ({ features, loading, reload }),
    [features, loading, reload],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAppFeatures(): FeatureValue {
  const v = useContext(Ctx);
  if (!v) throw new Error('useAppFeatures 는 AppFeatureProvider 안에서만 쓸 수 있어요.');
  return v;
}
