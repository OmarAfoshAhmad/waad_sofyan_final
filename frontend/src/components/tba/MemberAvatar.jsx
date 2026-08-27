import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { Avatar, Box, CircularProgress, Tooltip } from '@mui/material';
import VIPIcon from '@mui/icons-material/Star';
import FlashIcon from '@mui/icons-material/Bolt';
import axiosClient from 'utils/axios';

/**
 * MemberAvatar - Reusable Enterprise Component
 *
 * A robust component for displaying member photos with:
 * - Automatic URL resolution and cache busting
 * - Graceful fallback to name initials
 * - Loading states and error handling
 * - Theme-consistent styling
 *
 * @param {Object} props
 * @param {Object} props.member - Member data object
 * @param {number|string} [props.size=40] - Avatar size
 * @param {Object} [props.sx] - Additional MUI styles
 * @param {string} [props.refreshTrigger] - Optional seed to force refresh
 */
const MemberAvatar = ({ member, size = 40, sx = {}, refreshTrigger, onClick }) => {
  const [imgError, setImgError] = useState(false);
  const [loading, setLoading] = useState(false);
  const [photoUrl, setPhotoUrl] = useState(null);

  // 1. Resolve effective photo URL with authenticated fetch for protected endpoints
  useEffect(() => {
    let isMounted = true;
    let objectUrlToRevoke = null;

    const resolvePhoto = async () => {
      if (!member?.photoUrl) {
        if (isMounted) {
          setPhotoUrl(null);
          setImgError(false);
          setLoading(false);
        }
        return;
      }

      const originalUrl = member.photoUrl;

      // Local previews should be used directly
      if (originalUrl.startsWith('blob:') || originalUrl.startsWith('data:')) {
        if (isMounted) {
          setPhotoUrl(originalUrl);
          setImgError(false);
          setLoading(false);
        }
        return;
      }

      const hasTimestamp = originalUrl.includes('?t=') || originalUrl.includes('&t=');
      const timestamp = refreshTrigger || new Date().getTime();
      const resolvedUrl = hasTimestamp ? originalUrl : `${originalUrl}${originalUrl.includes('?') ? '&' : '?'}t=${timestamp}`;

      if (isMounted) {
        setLoading(true);
      }

      try {
        const response = await axiosClient.get(resolvedUrl, {
          responseType: 'blob',
          headers: {
            Accept: 'image/*'
          }
        });

        objectUrlToRevoke = URL.createObjectURL(response.data);

        if (isMounted) {
          setPhotoUrl(objectUrlToRevoke);
          setImgError(false);
        }
      } catch (error) {
        if (isMounted) {
          setPhotoUrl(resolvedUrl);
          setImgError(false);
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    };

    resolvePhoto();

    return () => {
      isMounted = false;
      if (objectUrlToRevoke) {
        URL.revokeObjectURL(objectUrlToRevoke);
      }
    };
  }, [member?.photoUrl, member?.id, refreshTrigger]);

  useEffect(() => {
    if (!member?.photoUrl) {
      setPhotoUrl(null);
      setImgError(false);
    }
  }, [member?.photoUrl]);

  // 2. Derive initials for professional Fallback
  const getInitials = () => {
    if (!member?.fullName) return '?';
    return member.fullName.trim().charAt(0).toUpperCase();
  };

  const handleImageError = () => {
    // Suppress warning for expected 404s/fallbacks
    // console.debug(`[MemberAvatar] Failed to load image for: ${member?.fullName}`);
    setImgError(true);
    setLoading(false);
  };

  const handleLoadStart = () => setLoading(true);
  const handleLoadEnd = () => setLoading(false);

  // 3. Render logic
  return (
    <Box
      onClick={onClick}
      sx={{
        position: 'relative',
        width: size,
        height: size,
        display: 'inline-flex',
        cursor: onClick ? 'pointer' : 'default'
      }}
    >
      <Avatar
        src={imgError ? undefined : photoUrl}
        alt={member?.fullName}
        onError={handleImageError}
        onLoad={handleLoadEnd}
        sx={{
          width: size,
          height: size,
          fontSize: typeof size === 'number' ? size * 0.45 : '1rem',
          bgcolor: 'primary.lighter',
          color: 'primary.main',
          fontWeight: 'bold',
          border: '2px solid',
          borderColor: 'primary.light',
          ...sx
        }}
      >
        {getInitials()}
      </Avatar>

      {loading && (
        <CircularProgress
          size={size}
          thickness={2}
          sx={{
            position: 'absolute',
            top: 0,
            left: 0,
            zIndex: 1,
            color: 'primary.main',
            opacity: 0.5
          }}
        />
      )}

      {/* VIP/Urgent Badges */}
      {member?.isVip && (
        <Box
          sx={{
            position: 'absolute',
            bottom: -2,
            right: -2,
            bgcolor: '#ffc107',
            borderRadius: '50%',
            width: size * 0.35,
            height: size * 0.35,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: 1,
            border: '1.5px solid #fff',
            zIndex: 2
          }}
        >
          <VIPIcon sx={{ color: '#fff', fontSize: size * 0.25 }} />
        </Box>
      )}
      {!member?.isVip && member?.isUrgent && (
        <Box
          sx={{
            position: 'absolute',
            bottom: -2,
            right: -2,
            bgcolor: '#ff5722',
            borderRadius: '50%',
            width: size * 0.35,
            height: size * 0.35,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: 1,
            border: '1.5px solid #fff',
            zIndex: 2
          }}
        >
          <FlashIcon sx={{ color: '#fff', fontSize: size * 0.25 }} />
        </Box>
      )}
    </Box>
  );
};

MemberAvatar.propTypes = {
  member: PropTypes.shape({
    id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    fullName: PropTypes.string,
    photoUrl: PropTypes.string
  }),
  size: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  sx: PropTypes.object,
  refreshTrigger: PropTypes.string,
  onClick: PropTypes.func
};

export default MemberAvatar;
