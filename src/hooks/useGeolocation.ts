import { useState, useEffect, useCallback } from 'react';

interface GeolocationState {
  loading: boolean;
  error: string | null;
  location: { lat: number; lng: number } | null;
  accuracy: number | null;
  timestamp: number | null;
}

interface UseGeolocationReturn extends GeolocationState {
  requestLocation: () => void;
  hasPermission: boolean | null;
}

export function useGeolocation(): UseGeolocationReturn {
  const [state, setState] = useState<GeolocationState>({
    loading: false,
    error: null,
    location: null,
    accuracy: null,
    timestamp: null,
  });
  const [hasPermission, setHasPermission] = useState<boolean | null>(null);

  const requestLocation = useCallback(() => {
    if (!navigator.geolocation) {
      setState((prev) => ({
        ...prev,
        loading: false,
        error: '您的浏览器不支持地理位置功能',
      }));
      return;
    }

    setState((prev) => ({ ...prev, loading: true, error: null }));

    navigator.geolocation.getCurrentPosition(
      (position) => {
        setState({
          loading: false,
          error: null,
          location: {
            lat: position.coords.latitude,
            lng: position.coords.longitude,
          },
          accuracy: position.coords.accuracy,
          timestamp: position.timestamp,
        });
        setHasPermission(true);
      },
      (error) => {
        let errorMessage: string;
        switch (error.code) {
          case error.PERMISSION_DENIED:
            errorMessage = '定位权限被拒绝，请在浏览器设置中允许定位权限';
            setHasPermission(false);
            break;
          case error.POSITION_UNAVAILABLE:
            errorMessage = '无法获取位置信息，请检查设备定位是否开启';
            break;
          case error.TIMEOUT:
            errorMessage = '获取位置超时，请重试';
            break;
          default:
            errorMessage = '获取位置时发生未知错误';
        }
        setState((prev) => ({
          ...prev,
          loading: false,
          error: errorMessage,
        }));
      },
      {
        enableHighAccuracy: true,
        timeout: 10000,
        maximumAge: 60000,
      }
    );
  }, []);

  useEffect(() => {
    if ('permissions' in navigator) {
      navigator.permissions.query({ name: 'geolocation' }).then((result) => {
        setHasPermission(result.state === 'granted');
        result.addEventListener('change', () => {
          setHasPermission(result.state === 'granted');
        });
      });
    }
  }, []);

  return {
    ...state,
    requestLocation,
    hasPermission,
  };
}

export function calculateDistance(
  lat1: number,
  lng1: number,
  lat2: number,
  lng2: number
): number {
  const R = 6371000;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLng = ((lng2 - lng1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLng / 2) *
      Math.sin(dLng / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

export function formatDistance(distance: number): string {
  if (distance < 1000) {
    return `${Math.round(distance)}m`;
  }
  return `${(distance / 1000).toFixed(1)}km`;
}
