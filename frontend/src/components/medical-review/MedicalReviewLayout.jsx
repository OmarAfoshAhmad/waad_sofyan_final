/**
 * ═══════════════════════════════════════════════════════════════════════════
 * 🩺 MEDICAL REVIEW LAYOUT - 3-Panel Desktop-First Design
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Professional TPA/HIS-style layout for medical reviewers
 *
 * Desktop Layout (≥1280px):
 * ┌──────────────┬──────────────────────┬──────────────────────┐
 * │ LEFT (Fixed) │ CENTER (Primary)     │ RIGHT (Fixed)        │
 * │ Documents    │ Medical Data         │ Decision Panel       │
 * │ 360px        │ Flexible             │ 360px                │
 * └──────────────┴──────────────────────┴──────────────────────┘
 *
 * Tablet (768-1279px):
 * - Documents collapsible
 * - Decision panel sticky bottom
 *
 * Mobile (<768px):
 * - Single column
 * - Tabs: Documents | Data | Decision
 *
 * @version 1.0
 * @date 2026-02-07
 */

import { useState, memo } from 'react';
import PropTypes from 'prop-types';
import { Box, Grid, IconButton, Tooltip, Fab, Badge, useMediaQuery, useTheme, Tabs, Tab, Paper, alpha } from '@mui/material';
import {
  ChevronLeft as CollapseLeftIcon,
  ChevronRight as ExpandLeftIcon,
  AttachFile as DocumentsIcon,
  Gavel as DecisionIcon,
  Description as DataIcon
} from '@mui/icons-material';

// ============================================================================
// CONSTANTS
// ============================================================================

const PANEL_WIDTH_DESKTOP = 360;
const GAP_DESKTOP = 16;
const MOBILE_TAB_VALUES = {
  DOCUMENTS: 0,
  DATA: 1,
  DECISION: 2
};

// ============================================================================
// MAIN COMPONENT
// ============================================================================

/**
 * Medical Review Layout
 *
 * Provides consistent 3-panel layout for Claims, Pre-Auth, and Approvals
 */
const MedicalReviewLayout = ({
  // Content slots
  leftPanel,
  centerPanel,
  rightPanel,

  // Configuration
  showLeftPanel = true,
  showRightPanel = true,
  leftPanelWidth = PANEL_WIDTH_DESKTOP,
  rightPanelWidth = PANEL_WIDTH_DESKTOP,
  gap = GAP_DESKTOP,
  minHeight = 'calc(100vh - 200px)',

  // Badge counts
  documentsCount = 0,

  // Responsive behavior
  collapsible = true,
  defaultCollapsed = false
}) => {
  const theme = useTheme();

  // Responsive breakpoints
  const isDesktop = useMediaQuery(theme.breakpoints.up('md')); // ≥900px
  const isLargeDesktop = useMediaQuery(theme.breakpoints.up('lg')); // ≥1200px
  const isMobile = useMediaQuery(theme.breakpoints.down('sm')); // <600px

  // State
  const [leftCollapsed, setLeftCollapsed] = useState(defaultCollapsed);
  const [mobileTab, setMobileTab] = useState(MOBILE_TAB_VALUES.DATA);

  // Calculate panel widths
  const effectiveLeftWidth = leftCollapsed ? 0 : leftPanelWidth;
  const effectiveRightWidth = showRightPanel ? rightPanelWidth : 0;
  const centerWidth = `calc(100% - ${effectiveLeftWidth + effectiveRightWidth + gap * 2}px)`;

  // Mobile layout
  if (isMobile) {
    return (
      <Box sx={{ minHeight }}>
        {/* Mobile Tabs */}
        <Paper square elevation={1} sx={{ mb: '1.0rem' }}>
          <Tabs
            value={mobileTab}
            onChange={(_, value) => setMobileTab(value)}
            variant="fullWidth"
            indicatorColor="primary"
            textColor="primary"
          >
            <Tab
              icon={
                <Badge badgeContent={documentsCount} color="primary">
                  <DocumentsIcon />
                </Badge>
              }
              label="المستندات"
              value={MOBILE_TAB_VALUES.DOCUMENTS}
            />
            <Tab icon={<DataIcon />} label="البيانات" value={MOBILE_TAB_VALUES.DATA} />
            <Tab icon={<DecisionIcon />} label="القرار" value={MOBILE_TAB_VALUES.DECISION} />
          </Tabs>
        </Paper>

        {/* Tab Panels */}
        <Box sx={{ minHeight }}>
          {mobileTab === MOBILE_TAB_VALUES.DOCUMENTS && leftPanel}
          {mobileTab === MOBILE_TAB_VALUES.DATA && centerPanel}
          {mobileTab === MOBILE_TAB_VALUES.DECISION && rightPanel}
        </Box>
      </Box>
    );
  }

  // Tablet layout (simplified 2-column)
  if (!isLargeDesktop) {
    return (
      <Box sx={{ minHeight }}>
        <Grid container spacing={gap / 8}>
          {/* Main content */}
          <Grid size={{ xs: 12, md: showRightPanel ? 7 : 12 }}>
            <Box sx={{ minHeight }}>{centerPanel}</Box>
          </Grid>

          {/* Right panel (sticky) */}
          {showRightPanel && (
            <Grid size={{ xs: 12, md: 5 }}>
              <Box sx={{ position: 'sticky', top: gap, minHeight }}>{rightPanel}</Box>
            </Grid>
          )}
        </Grid>

        {/* Floating Documents Button (Tablet) */}
        {showLeftPanel && (
          <Tooltip title="المستندات">
            <Fab
              color="primary"
              size="medium"
              onClick={() => setLeftCollapsed(!leftCollapsed)}
              sx={{
                position: 'fixed',
                bottom: gap * 2,
                left: gap * 2,
                zIndex: theme.zIndex.speedDial
              }}
            >
              <Badge badgeContent={documentsCount} color="error">
                <DocumentsIcon />
              </Badge>
            </Fab>
          </Tooltip>
        )}

        {/* Collapsed Documents Panel (Drawer-like) */}
        {showLeftPanel && !leftCollapsed && (
          <Paper
            elevation={8}
            sx={{
              position: 'fixed',
              top: gap,
              left: gap,
              bottom: gap,
              width: leftPanelWidth,
              zIndex: theme.zIndex.drawer,
              overflow: 'hidden'
            }}
          >
            <Box sx={{ position: 'relative', height: '100%' }}>
              {/* Close button */}
              <IconButton
                onClick={() => setLeftCollapsed(true)}
                sx={{
                  position: 'absolute',
                  top: '4.0rem',
                  right: '0.375rem',
                  zIndex: 1,
                  bgcolor: 'background.paper',
                  '&:hover': {
                    bgcolor: 'action.hover'
                  }
                }}
                size="small"
              >
                <CollapseLeftIcon />
              </IconButton>
              {leftPanel}
            </Box>
          </Paper>
        )}
      </Box>
    );
  }

  // Desktop layout (3-panel)
  return (
    <Box
      sx={{
        display: 'flex',
        gap: `${gap}px`,
        minHeight,
        position: 'relative'
      }}
    >
      {/* Left Panel - Documents (Fixed) */}
      {showLeftPanel && !leftCollapsed && (
        <Box
          sx={{
            width: leftPanelWidth,
            flexShrink: 0,
            position: 'sticky',
            top: gap,
            alignSelf: 'flex-start',
            maxHeight: minHeight
          }}
        >
          {leftPanel}

          {/* Collapse button */}
          {collapsible && (
            <Tooltip title="إخفاء المستندات">
              <IconButton
                onClick={() => setLeftCollapsed(true)}
                size="small"
                sx={{
                  position: 'absolute',
                  top: '4.0rem',
                  right: -12,
                  bgcolor: 'background.paper',
                  boxShadow: 2,
                  border: 1,
                  borderColor: 'divider',
                  '&:hover': {
                    bgcolor: 'action.hover',
                    boxShadow: 4
                  },
                  zIndex: 1
                }}
              >
                <CollapseLeftIcon />
              </IconButton>
            </Tooltip>
          )}
        </Box>
      )}

      {/* Expand button (when collapsed) */}
      {showLeftPanel && leftCollapsed && collapsible && (
        <Tooltip title="إظهار المستندات">
          <Fab
            color="primary"
            size="small"
            onClick={() => setLeftCollapsed(false)}
            sx={{
              position: 'fixed',
              top: gap * 2,
              left: gap,
              zIndex: theme.zIndex.speedDial,
              boxShadow: 4
            }}
          >
            <Badge badgeContent={documentsCount} color="error">
              <ExpandLeftIcon />
            </Badge>
          </Fab>
        </Tooltip>
      )}

      {/* Center Panel - Medical Data (Flexible) */}
      <Box
        sx={{
          flex: 1,
          minWidth: 0, // Allow shrinking
          maxWidth: leftCollapsed ? `calc(100% - ${effectiveRightWidth + gap}px)` : centerWidth
        }}
      >
        {centerPanel}
      </Box>

      {/* Right Panel - Decision (Fixed) */}
      {showRightPanel && (
        <Box
          sx={{
            width: rightPanelWidth,
            flexShrink: 0,
            position: 'sticky',
            top: gap,
            alignSelf: 'flex-start',
            maxHeight: minHeight
          }}
        >
          {rightPanel}
        </Box>
      )}
    </Box>
  );
};

MedicalReviewLayout.propTypes = {
  /** Left panel content (documents) */
  leftPanel: PropTypes.node,
  /** Center panel content (medical data) */
  centerPanel: PropTypes.node.isRequired,
  /** Right panel content (decision) */
  rightPanel: PropTypes.node,
  /** Show left panel */
  showLeftPanel: PropTypes.bool,
  /** Show right panel */
  showRightPanel: PropTypes.bool,
  /** Left panel width (px) */
  leftPanelWidth: PropTypes.number,
  /** Right panel width (px) */
  rightPanelWidth: PropTypes.number,
  /** Gap between panels (px) */
  gap: PropTypes.number,
  /** Minimum height */
  minHeight: PropTypes.string,
  /** Documents count for badge */
  documentsCount: PropTypes.number,
  /** Allow collapsing left panel */
  collapsible: PropTypes.bool,
  /** Default collapsed state */
  defaultCollapsed: PropTypes.bool
};

export default memo(MedicalReviewLayout);


