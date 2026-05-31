'use client';
import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';

export default function RootPage() {
  const router = useRouter();
  const [isReady, setIsReady] = useState(false);

  useEffect(() => {
    // Wait for client to be ready and check auth from localStorage
    const checkAuth = () => {
      try {
        const raw = localStorage.getItem('admin-auth');
        const authState = raw ? JSON.parse(raw)?.state : null;
        const hasToken = authState?.token && authState?.isAuthenticated;

        if (hasToken) {
          router.replace('/dashboard');
        } else {
          router.replace('/login');
        }
      } catch (e) {
        // Default to login if any error
        router.replace('/login');
      }
      setIsReady(true);
    };

    // Small delay to ensure localStorage is ready
    const timer = setTimeout(checkAuth, 100);
    return () => clearTimeout(timer);
  }, [router]);

  // Show nothing while redirecting
  if (!isReady) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[#F8FAFC]">
        <div className="text-center">
          <div className="w-12 h-12 border-4 border-[#E2E8F0] border-t-[#059669] rounded-full animate-spin mx-auto"></div>
        </div>
      </div>
    );
  }

  return null;
}