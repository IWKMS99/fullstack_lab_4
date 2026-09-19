const DEFAULT_BASE_URL = typeof window === 'undefined' ? 'http://localhost:8080' : window.location.origin;

const trimTrailingSlash = (value: string) => value.replace(/\/+$/, '');

export const getPublicBaseUrl = () => {
  const raw = import.meta.env.VITE_PUBLIC_BASE_URL;
  if (!raw || typeof raw !== 'string') {
    return DEFAULT_BASE_URL;
  }
  return trimTrailingSlash(raw);
};

export const absoluteUrl = (path: string) => {
  const base = getPublicBaseUrl();
  if (!path.startsWith('/')) {
    return `${base}/${path}`;
  }
  return `${base}${path}`;
};

export const defaultOgImageUrl = absoluteUrl('/og-default.svg');
