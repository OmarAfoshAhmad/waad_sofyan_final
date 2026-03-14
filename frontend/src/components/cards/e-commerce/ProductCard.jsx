import PropTypes from 'prop-types';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

// material-ui
import Button from '@mui/material/Button';
import CardContent from '@mui/material/CardContent';
import CardMedia from '@mui/material/CardMedia';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Grid from '@mui/material/Grid';
import Rating from '@mui/material/Rating';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

// project imports
import MainCard from 'components/MainCard';
import IconButton from 'components/@extended/IconButton';
import SkeletonProductPlaceholder from 'components/cards/skeleton/ProductPlaceholder';
import { useGetCart, addToCart } from 'api/cart';
import { openSnackbar } from 'api/snackbar';
import { ImagePath, getImageUrl } from 'utils/getImageUrl';

// assets
import HeartOutlined from '@ant-design/icons/HeartOutlined';
import HeartFilled from '@ant-design/icons/HeartFilled';

// ==============================|| PRODUCT CARD ||============================== //

export default function ProductCard({ id, color, name, brand, offer, isStock, image, description, offerPrice, salePrice, rating }) {
  const { cart } = useGetCart();

  const [wishlisted, setWishlisted] = useState(false);

  const addCart = () => {
    addToCart({ id, name, image, salePrice, offerPrice, color, size: 8, quantity: 1, description }, cart.products);
    openSnackbar({
      open: true,
      message: 'Add To Cart Success',
      variant: 'alert',

      alert: {
        color: 'success'
      }
    });
  };

  const addToFavourite = () => {
    setWishlisted(!wishlisted);
    openSnackbar({
      open: true,
      message: !wishlisted ? 'Added to favorites' : 'Removed from favorites',
      variant: 'alert',

      alert: {
        color: 'success'
      }
    });
  };

  const [isLoading, setLoading] = useState(true);
  useEffect(() => {
    setLoading(false);
  }, []);

  return (
    <>
      {isLoading ? (
        <SkeletonProductPlaceholder />
      ) : (
        <MainCard content={false} boxShadow sx={{ '&:hover': { transform: 'scale3d(1.02, 1.02, 1)', transition: 'all .4s ease-in-out' } }}>
          <Box sx={{ width: '15.625rem', m: 'auto' }}>
            <CardMedia
              sx={{ height: '15.625rem', textDecoration: 'none', opacity: isStock ? 1 : 0.25 }}
              image={image && getImageUrl(`${image}`, ImagePath.ECOMMERCE)}
              component={Link}
              to={`/apps/e-commerce/product-details/${id}`}
            />
          </Box>
          <Stack
            direction="row"
            sx={{ alignItems: 'center', justifyContent: 'space-between', width: '0.0625rem', position: 'absolute', top: 0, pt: '0.875rem', pl: '1.0rem', pr: 1 }}
          >
            {!isStock && <Chip variant="light" color="error" size="small" label="Sold out" />}
            {offer && <Chip label={offer} variant="combined" color="success" size="small" />}
            <IconButton
              color={wishlisted ? 'error' : 'secondary'}
              sx={{ ml: 'auto', '&:hover': { bgcolor: 'transparent' } }}
              onClick={addToFavourite}
            >
              {wishlisted ? <HeartFilled style={{ fontSize: '1.15rem' }} /> : <HeartOutlined style={{ fontSize: '1.15rem' }} />}
            </IconButton>
          </Stack>
          <Divider />
          <CardContent sx={{ p: '1.0rem' }}>
            <Grid container spacing={2}>
              <Grid size={12}>
                <Stack>
                  <Typography
                    component={Link}
                    to={`/apps/e-commerce/product-details/${id}`}
                    color="text.primary"
                    variant="h5"
                    sx={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', display: 'block', textDecoration: 'none' }}
                  >
                    {name}
                  </Typography>
                  <Typography variant="h6" color="text.secondary">
                    {brand}
                  </Typography>
                </Stack>
              </Grid>
              <Grid size={12}>
                <Stack direction="row" sx={{ gap: '0.875rem', alignItems: 'flex-end', justifyContent: 'space-between', flexWrap: 'wrap' }}>
                  <Stack>
                    <Stack direction="row" sx={{ gap: 1, alignItems: 'center' }}>
                      <Typography variant="h5">${offerPrice}</Typography>
                      {salePrice && (
                        <Typography variant="h6" color="text.secondary" sx={{ textDecoration: 'line-through' }}>
                          ${salePrice}
                        </Typography>
                      )}
                    </Stack>
                    <Stack direction="row" sx={{ alignItems: 'flex-start' }}>
                      <Rating precision={0.5} name="size-small" value={rating} size="small" readOnly />
                      <Typography variant="caption">({rating?.toFixed(1)})</Typography>
                    </Stack>
                  </Stack>

                  <Button
                    variant="contained"
                    onClick={addCart}
                    disabled={!isStock}
                    sx={(theme) => ({ '&.Mui-disabled': { color: theme.vars.palette.grey[400] } })}
                  >
                    {!isStock ? 'Sold Out' : 'Add to Cart'}
                  </Button>
                </Stack>
              </Grid>
            </Grid>
          </CardContent>
        </MainCard>
      )}
    </>
  );
}

ProductCard.propTypes = {
  id: PropTypes.any,
  color: PropTypes.any,
  name: PropTypes.any,
  brand: PropTypes.any,
  offer: PropTypes.any,
  isStock: PropTypes.any,
  image: PropTypes.any,
  description: PropTypes.any,
  offerPrice: PropTypes.any,
  salePrice: PropTypes.any,
  rating: PropTypes.any
};
