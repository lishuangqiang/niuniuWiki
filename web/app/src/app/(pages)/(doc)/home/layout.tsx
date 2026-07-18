import StoreProvider from '@/provider';
import { getShareV1NodeList } from '@/request/ShareNode';
import {
  convertToTree,
  filterEmptyFolders,
  parseNodeListResponse,
} from '@/utils/tree';

/**
 * 为首页注入与文档阅读页一致的栏目和文档树，使首页内容卡片始终来自真实知识库。
 *
 * @author 程序员牛肉
 * @since 2026-07-16
 */
export default async function HomeLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  const nodeListRaw = (await getShareV1NodeList()) ?? [];
  const { isGrouped, navList, navDataMap, defaultNavId } =
    parseNodeListResponse(nodeListRaw);
  const nodeListForTree = isGrouped
    ? (navDataMap[defaultNavId || ''] ??
      navDataMap[Object.keys(navDataMap)[0]] ??
      [])
    : nodeListRaw;
  const tree = filterEmptyFolders(convertToTree(nodeListForTree as any));

  return (
    <StoreProvider
      nodeList={
        (Array.isArray(nodeListRaw) && !isGrouped ? nodeListRaw : []) as any
      }
      tree={tree}
      navList={navList}
      selectedNavId={defaultNavId || navList[0]?.id || ''}
      navDataMap={navDataMap}
    >
      {children}
    </StoreProvider>
  );
}
