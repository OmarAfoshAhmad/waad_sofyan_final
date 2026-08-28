import PropTypes from 'prop-types';
import Box from '@mui/material/Box';
import Skeleton from '@mui/material/Skeleton';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';

/**
 * One member's general ceiling, as a list row shows it.
 *
 * The headline number is reservableAvailable -- what may still be committed --
 * and never the remaining-consumption figure, because money already held by an
 * approved pre-authorization is not available to commit again.
 *
 * The bar is split rather than proportional to a single percentage. Consumed
 * and held are different facts: one is money gone, the other is money spoken
 * for and still returnable. A single "unavailable" percentage would add them
 * and hide the difference that decides what happens next.
 */

const MODES = {
  FOUND: 'FOUND',
  UNLIMITED: 'UNLIMITED',
  NOT_CONFIGURED: 'NOT_CONFIGURED',
  UNAVAILABLE: 'UNAVAILABLE'
};

const ALERT_COLOURS = {
  NORMAL: 'success.main',
  WARNING: 'warning.main',
  CRITICAL: 'error.main',
  EXHAUSTED: 'error.main',
  EXCEEDED: 'error.main'
};

const formatAmount = (value) => {
  if (value === null || value === undefined) {
    return null;
  }
  return `${Number(value).toLocaleString('en-US', { maximumFractionDigits: 0 })} د.ل`;
};

/** A quiet line for every state that has no figures to draw. */
function NoFigures({ text, hint }) {
  const body = (
    <Typography variant="caption" color="text.secondary">
      {text}
    </Typography>
  );
  return hint ? <Tooltip title={hint}>{body}</Tooltip> : body;
}

NoFigures.propTypes = {
  text: PropTypes.string.isRequired,
  hint: PropTypes.string
};

export default function MemberCeilingCell({ summary, loading }) {
  if (loading) {
    return <Skeleton variant="rounded" width="7.5rem" height="2.25rem" />;
  }
  if (!summary || summary.mode === MODES.UNAVAILABLE) {
    return (
      <Stack direction="row" spacing={0.5} alignItems="center" justifyContent="center">
        <ErrorOutlineIcon sx={{ fontSize: '1rem', color: 'text.disabled' }} />
        <NoFigures text="غير متاح" hint="تعذّرت قراءة الرصيد. أعد المحاولة من صفحة العضو." />
      </Stack>
    );
  }
  if (summary.mode === MODES.NOT_CONFIGURED) {
    return <NoFigures text="لا يوجد سقف" hint="لا توجد وثيقة سارية للعضو بهذا التاريخ" />;
  }
  if (summary.mode === MODES.UNLIMITED) {
    return <NoFigures text="بلا سقف" hint="الوثيقة لا تحدد سقفاً سنوياً" />;
  }

  const limit = Number(summary.limit) || 0;
  const committed = Number(summary.committed) || 0;
  const reserved = Number(summary.reserved) || 0;
  const available = Number(summary.reservableAvailable);
  const exceeded = summary.alertStatus === 'EXCEEDED';

  // Widths are shares of the ceiling, so they cannot exceed it even when
  // spending has. An overspend is reported by the warning line and by the
  // negative number, never by a bar drawn past its own track.
  const committedShare = limit > 0 ? Math.min(100, (committed / limit) * 100) : 0;
  const reservedShare = limit > 0 ? Math.min(100 - committedShare, (reserved / limit) * 100) : 0;

  return (
    <Stack spacing={0.25} sx={{ minWidth: '9rem' }}>
      <Typography
        variant="body2"
        fontWeight="bold"
        color={exceeded ? 'error.main' : ALERT_COLOURS[summary.alertStatus] || 'text.primary'}
      >
        {formatAmount(available)}
      </Typography>
      <Typography variant="caption" color="text.secondary">
        المتاح لالتزام جديد من {formatAmount(limit)}
      </Typography>

      <Tooltip
        title={`السقف ${formatAmount(limit)} · مستهلك ${formatAmount(committed)} · محجوز ${formatAmount(reserved)}`}
      >
        <Box
          sx={{
            display: 'flex',
            height: '0.375rem',
            width: '100%',
            borderRadius: '0.1875rem',
            overflow: 'hidden',
            bgcolor: 'action.hover'
          }}
        >
          <Box sx={{ width: `${committedShare}%`, bgcolor: 'primary.dark' }} />
          {/* Held money: the same hue, striped, so it reads as related to
              consumption without being mistaken for it. */}
          <Box
            sx={{
              width: `${reservedShare}%`,
              backgroundImage: (theme) =>
                `repeating-linear-gradient(45deg, ${theme.palette.primary.light} 0 0.1875rem, ${theme.palette.primary.lighter || theme.palette.background.paper} 0.1875rem 0.375rem)`
            }}
          />
        </Box>
      </Tooltip>

      <Typography variant="caption" color="text.secondary">
        مستهلك {formatAmount(committed)} · محجوز {formatAmount(reserved)}
      </Typography>

      {exceeded && (
        <Typography variant="caption" color="error.main" fontWeight="medium">
          تجاوز {formatAmount(Math.abs(Number(summary.actualRemaining)))}
        </Typography>
      )}
    </Stack>
  );
}

MemberCeilingCell.propTypes = {
  summary: PropTypes.shape({
    mode: PropTypes.string,
    limit: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    committed: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    reserved: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    actualRemaining: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    reservableAvailable: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    alertStatus: PropTypes.string
  }),
  loading: PropTypes.bool
};
