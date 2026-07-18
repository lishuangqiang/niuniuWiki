import { ConstsLicenseEdition } from '@/request/types';
import { SxProps } from '@mui/material';
import React from 'react';

/**
 * 全功能模式下保留原组件签名，直接渲染内容，不再展示版本遮罩。
 */
const VersionMask = ({
  children,
}: {
  permission?: ConstsLicenseEdition[];
  children?: React.ReactNode;
  wrapperSx?: SxProps;
  sx?: SxProps;
}) => <>{children}</>;

/**
 * 版本提示已取消，所有功能始终可用。
 */
export const VersionCanUse = (_props: {
  permission?: ConstsLicenseEdition[];
  sx?: SxProps;
  mode?: 'icon' | 'text';
}) => null;

export default VersionMask;
