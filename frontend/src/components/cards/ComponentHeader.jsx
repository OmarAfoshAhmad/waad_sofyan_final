import PropTypes from 'prop-types';
// material-ui
import Typography from '@mui/material/Typography';
import Grid from '@mui/material/Grid';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Box from '@mui/material/Box';

// assets
import GlobalOutlined from '@ant-design/icons/GlobalOutlined';
import NodeExpandOutlined from '@ant-design/icons/NodeExpandOutlined';

export default function ComponentHeader({ title, caption, directory, link }) {
  return (
    <Box sx={{ pl: { xs: 1.5, md: 2, lg: 3 }, pr: 1 }}>
      <Stack sx={{ gap: '0.625rem' }}>
        <Typography variant="h2">{title}</Typography>
        {caption && (
          <Typography variant="h6" color="text.secondary">
            {caption}
          </Typography>
        )}
      </Stack>
      <Grid container spacing={0.75} sx={{ mt: '1.25rem' }}>
        {directory && (
          <Grid size={12}>
            <Typography variant="caption" color="text.secondary">
              <NodeExpandOutlined style={{ marginRight: '0.625rem' }} />
              {directory}
            </Typography>
          </Grid>
        )}
        {link && (
          <Grid size={12}>
            <Link variant="caption" color="primary" href={link} target="_blank">
              <GlobalOutlined style={{ marginRight: '0.625rem' }} />
              {link}
            </Link>
          </Grid>
        )}
      </Grid>
    </Box>
  );
}

ComponentHeader.propTypes = { title: PropTypes.string, caption: PropTypes.string, directory: PropTypes.string, link: PropTypes.string };
