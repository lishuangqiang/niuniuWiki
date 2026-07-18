import { Paper, SxProps } from '@mui/material';

interface CardProps {
  sx?: SxProps;
  children: React.ReactNode;
  onClick?: () => void;
  className?: string;
}
const Card = ({ sx, children, onClick, className }: CardProps) => {
  return (
    <Paper
      className={`paper-item ${className}`}
      sx={{
        borderRadius: '18px',
        boxShadow: '0 10px 35px rgba(0, 0, 0, 0.05)',
        border: '1px solid rgba(60, 60, 67, 0.08)',
        overflow: 'hidden',
        ...sx,
      }}
      onClick={onClick ? onClick : undefined}
    >
      {children}
    </Paper>
  );
};

export default Card;
