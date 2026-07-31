import React, { createContext, useContext, useEffect, useState, useCallback, useRef } from 'react';
import { useSnackbar } from 'notistack';
import useAuth from './useAuth';
import axiosClient from 'utils/axios';

const ReviewerContext = createContext(null);

export const useReviewer = () => {
  const context = useContext(ReviewerContext);
  if (!context) {
    throw new Error('useReviewer must be used within a ReviewerProvider');
  }
  return context;
};

// Web Audio API helper for beep sound
const playBeep = () => {
  try {
    const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    const oscillator = audioCtx.createOscillator();
    const gainNode = audioCtx.createGain();

    oscillator.connect(gainNode);
    gainNode.connect(audioCtx.destination);

    oscillator.type = 'square';
    oscillator.frequency.value = 400; // Hz

    gainNode.gain.setValueAtTime(0, audioCtx.currentTime);
    gainNode.gain.linearRampToValueAtTime(0.5, audioCtx.currentTime + 0.05);
    gainNode.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + 0.5);

    oscillator.start(audioCtx.currentTime);
    oscillator.stop(audioCtx.currentTime + 0.5);
  } catch (e) {
    console.error('Audio beep failed:', e);
  }
};

export const ReviewerProvider = ({ children }) => {
  const { user, isLoggedIn } = useAuth();
  const { enqueueSnackbar } = useSnackbar();
  
  // SSE EventSource ref
  const eventSourceRef = useRef(null);
  
  // Trigger to refetch inbox data
  const [inboxRefreshTrigger, setInboxRefreshTrigger] = useState(0);

  // --- Settings ---
  // We save them in localStorage
  const getInitialSetting = (key, defaultValue) => {
    const stored = localStorage.getItem(key);
    return stored !== null ? JSON.parse(stored) : defaultValue;
  };

  const [audioEnabled, setAudioEnabled] = useState(() => getInitialSetting('reviewer_audio_enabled', true));
  const [inlineEditing, setInlineEditing] = useState(() => getInitialSetting('reviewer_inline_editing', false));

  // Update localStorage when settings change
  useEffect(() => {
    localStorage.setItem('reviewer_audio_enabled', JSON.stringify(audioEnabled));
  }, [audioEnabled]);

  useEffect(() => {
    localStorage.setItem('reviewer_inline_editing', JSON.stringify(inlineEditing));
  }, [inlineEditing]);

  const triggerRefresh = useCallback(() => {
    setInboxRefreshTrigger(prev => prev + 1);
  }, []);

  // --- SSE Connection ---
  useEffect(() => {
    // Only connect if user is logged in and has reviewer role (or admin)
    const roles = user?.roles || [];
    const canReview =
      roles.includes('ROLE_MEDICAL_REVIEWER') ||
      roles.includes('ROLE_REVIEWER') ||
      roles.includes('ROLE_INSURANCE_ADMIN') ||
      roles.includes('ROLE_SUPER_ADMIN');

    if (!isLoggedIn || !canReview) {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
      return;
    }

    if (!eventSourceRef.current) {
      // Connect to SSE. We need the API base url.
      // Since axiosClient has the baseURL, we can get it from there, but EventSource doesn't send headers easily.
      // WithCredentials allows sending cookies. 
      // If we use JWT in headers, EventSource doesn't support setting headers directly in browser API.
      // A workaround is to use a library or pass token in URL. 
      // But typically, the backend might accept token in query param or we might rely on cookies.
      // Assuming the backend has a way or we are using cookies. Let's try standard EventSource.
      
      const baseUrl = axiosClient.defaults.baseURL || '/api/v1';
      const sseUrl = `${baseUrl}/notifications/stream`;
      
      console.log('[SSE] Connecting to:', sseUrl);
      const es = new EventSource(sseUrl, { withCredentials: true });

      es.onopen = () => {
        console.log('[SSE] Connection opened');
      };

      es.addEventListener('connected', (e) => {
        console.log('[SSE] Connected event:', e.data);
      });

      es.addEventListener('notification', (e) => {
        try {
          const data = JSON.parse(e.data);
          console.log('[SSE] Notification received:', data);
          
          const isUrgent = data.priority === 'EMERGENCY' || data.priority === 'URGENT';
          
          // Play sound if urgent and audio enabled
          if (isUrgent && audioEnabled) {
            playBeep();
          }

          // Show snackbar
          enqueueSnackbar(data.message || 'يوجد إشعار جديد', {
            variant: data.priority === 'EMERGENCY' ? 'error' : (data.priority === 'URGENT' ? 'warning' : 'info'),
            autoHideDuration: isUrgent ? 6000 : 3000,
            style: isUrgent ? { fontWeight: 'bold' } : {}
          });

          // Trigger inbox refresh
          triggerRefresh();
        } catch (err) {
          console.error('[SSE] Failed to parse notification', err);
        }
      });

      es.onerror = (e) => {
        console.error('[SSE] Error:', e);
        // EventSource will automatically try to reconnect
      };

      eventSourceRef.current = es;
    }

    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
    };
  }, [isLoggedIn, user, enqueueSnackbar, audioEnabled, triggerRefresh]);

  const value = {
    audioEnabled,
    setAudioEnabled,
    inlineEditing,
    setInlineEditing,
    inboxRefreshTrigger,
    triggerRefresh
  };

  return <ReviewerContext.Provider value={value}>{children}</ReviewerContext.Provider>;
};
