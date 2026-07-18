import { createModel, getModelNameList, testModel, updateModel } from '@/api';
import type {
  CheckModelData as LocalCheckModelData,
  CreateModelData as LocalCreateModelData,
  GetModelNameData as LocalGetModelNameData,
  UpdateModelData as LocalUpdateModelData,
} from '@/api/type';
import { ModelProvider } from '@/constant/enums';
import { NiuniuWikiContractDomainModelListItem } from '@/request';
import type {
  ModelService as IModelService,
  Model,
  CheckModelReq as UICheckModelData,
  CreateModelReq as UICreateModelData,
  ListModelReq as UIGetModelNameData,
  ModelListItem as UIModelListItem,
  UpdateModelReq as UIUpdateModelData,
} from '@ctzhian/modelkit';
const modelkitModelTypeToLocal = (
  modelType: string,
): 'chat' | 'embedding' | 'rerank' | 'analysis' | 'analysis-vl' => {
  if (modelType === 'chat') return 'chat';
  if (modelType === 'llm') return 'chat';
  if (modelType === 'analysis') return 'analysis';
  if (modelType === 'analysis-vl') return 'analysis-vl';
  if (modelType === 'rerank') return 'rerank';
  if (modelType === 'reranker') return 'rerank';
  if (modelType === 'embedding') return 'embedding';
  return 'chat';
};

const BAILIAN_RECOMMENDED_EMBEDDING_MODEL = 'text-embedding-v4';

type BaiLianModelInput = {
  provider?: string;
  model_type?: string;
  model_name?: string;
  base_url?: string;
};

/**
 * 修正把百炼重排序模型误填到向量模型栏的历史配置。
 * 百炼的原生接口地址必须原样保留，由后端按 DashScope 协议构造请求体。
 *
 * @author 程序员牛肉
 * @since 2026-07-16
 */
const normalizeBaiLianEmbedding = <T extends BaiLianModelInput>(data: T): T => {
  if (
    data.provider !== 'BaiLian' ||
    modelkitModelTypeToLocal(data.model_type || '') !== 'embedding'
  ) {
    return data;
  }

  const isRerankModel = /(?:^|[-_])(rerank|re-rank)(?:$|[-_])/i.test(
    data.model_name || '',
  );

  return {
    ...data,
    model_name: isRerankModel
      ? BAILIAN_RECOMMENDED_EMBEDDING_MODEL
      : data.model_name,
  };
};

// 转换本地模型数据为 UI 模型数据
const convertLocalModelToUIModel = (
  localModel: NiuniuWikiContractDomainModelListItem | null,
): Model | null => {
  if (!localModel) return null;
  return {
    id: localModel.id,
    model_name: localModel.model,
    provider: localModel.provider,
    model_type: localModel.type,
    base_url: localModel.base_url,
    api_key: localModel.api_key,
    api_header: localModel.api_header,
    api_version: localModel.api_version,
    is_active: localModel.is_active,
    show_name: localModel.model,
    param: localModel.parameters,
  };
};

// 转换 UI 创建模型数据为本地创建模型数据
export const convertUICreateToLocalCreate = (
  uiModel: UICreateModelData,
): LocalCreateModelData => {
  const normalized = normalizeBaiLianEmbedding(uiModel);
  return {
    model: normalized.model_name || '',
    provider: normalized.provider as keyof typeof ModelProvider,
    type: modelkitModelTypeToLocal(normalized.model_type || ''),
    base_url: normalized.base_url || '',
    api_key: uiModel.api_key || '',
    api_header: uiModel.api_header || '',
    parameters: uiModel.param,
  };
};

// 转换 UI 更新模型数据为本地更新模型数据
export const convertUIUpdateToLocalUpdate = (
  uiModel: UIUpdateModelData,
): LocalUpdateModelData => {
  const normalized = normalizeBaiLianEmbedding(uiModel);
  return {
    id: uiModel.id || '',
    model: normalized.model_name || '',
    provider: normalized.provider as keyof typeof ModelProvider,
    base_url: normalized.base_url || '',
    api_key: uiModel.api_key || '',
    api_header: uiModel.api_header || '',
    api_version: uiModel.api_version || '',
    type: modelkitModelTypeToLocal(normalized.model_type || ''),
    parameters: uiModel.param,
  };
};

// 转换 UI 检查模型数据为本地检查模型数据
export const convertUICheckToLocalCheck = (
  uiCheck: UICheckModelData,
): LocalCheckModelData => {
  const normalized = normalizeBaiLianEmbedding(uiCheck);
  return {
    model: normalized.model_name || '',
    provider: normalized.provider as keyof typeof ModelProvider,
    type: modelkitModelTypeToLocal(normalized.model_type || ''),
    base_url: normalized.base_url || '',
    api_key: uiCheck.api_key || '',
    api_header: uiCheck.api_header || '',
    api_version: uiCheck.api_version || '',
    parameters: uiCheck.param || {},
  };
};

// 转换 UI 获取模型名称数据为本地获取模型名称数据
const convertUIGetModelNameToLocal = (
  uiData: UIGetModelNameData,
): LocalGetModelNameData => {
  return {
    provider: uiData.provider as keyof typeof ModelProvider,
    type: modelkitModelTypeToLocal(uiData.model_type || ''),
    base_url: uiData.base_url || '',
    api_key: uiData.api_key || '',
    api_header: uiData.api_header || '',
  };
};

// ModelService 实现
export const modelService: IModelService = {
  async createModel(data: UICreateModelData) {
    const localData = convertUICreateToLocalCreate(data);
    const result = await createModel(localData);

    // 创建成功后返回模型数据
    const model: Model = {
      id: result.id,
    };

    return { model };
  },

  async listModel(data: UIGetModelNameData) {
    const localData = convertUIGetModelNameToLocal(data);
    const result = await getModelNameList(localData);

    const models: UIModelListItem[] = result.models
      ? result.models.map(item => ({
          model: item.model || '',
        }))
      : [];
    const error: string = result.error || '';

    return { models, error };
  },

  async checkModel(data: UICheckModelData) {
    const localData = convertUICheckToLocalCheck(data);
    const result = await testModel(localData);

    const model: Model = {};
    const error: string = result.error || '';
    return { model, error };
  },

  async updateModel(data: UIUpdateModelData) {
    const localData = convertUIUpdateToLocalUpdate(data);
    await updateModel(localData);

    // 更新成功后返回模型数据
    const model: Model = {};

    return { model };
  },
};

export {
  convertLocalModelToUIModel,
  modelkitModelTypeToLocal,
  normalizeBaiLianEmbedding,
};
