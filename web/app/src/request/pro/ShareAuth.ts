/* eslint-disable */
/* tslint:disable */
// @ts-nocheck
/*
 * ---------------------------------------------------------------
 * ## THIS FILE WAS GENERATED VIA SWAGGER-TYPESCRIPT-API        ##
 * ##                                                           ##
 * ## AUTHOR: acacode                                           ##
 * ## SOURCE: https://github.com/acacode/swagger-typescript-api ##
 * ---------------------------------------------------------------
 */

import httpRequest, { ContentType, RequestParams } from "./httpClient";
import {
  DomainPWResponse,
  NiuniuWikiContractProApiShareV1AuthCASReq,
  NiuniuWikiContractProApiShareV1AuthCASResp,
  NiuniuWikiContractProApiShareV1AuthDingTalkReq,
  NiuniuWikiContractProApiShareV1AuthDingTalkResp,
  NiuniuWikiContractProApiShareV1AuthFeishuReq,
  NiuniuWikiContractProApiShareV1AuthFeishuResp,
  NiuniuWikiContractProApiShareV1AuthGitHubReq,
  NiuniuWikiContractProApiShareV1AuthGitHubResp,
  NiuniuWikiContractProApiShareV1AuthInfoResp,
  NiuniuWikiContractProApiShareV1AuthLDAPReq,
  NiuniuWikiContractProApiShareV1AuthLDAPResp,
  NiuniuWikiContractProApiShareV1AuthLogoutResp,
  NiuniuWikiContractProApiShareV1AuthOAuthReq,
  NiuniuWikiContractProApiShareV1AuthOAuthResp,
  NiuniuWikiContractProApiShareV1AuthWecomReq,
  NiuniuWikiContractProApiShareV1AuthWecomResp,
} from "./types";

/**
 * @description CAS登录
 *
 * @tags ShareAuth
 * @name PostShareProV1AuthCas
 * @summary CAS登录
 * @request POST:/share/pro/v1/auth/cas
 * @response `200` `(DomainPWResponse & {
    data?: NiuniuWikiContractProApiShareV1AuthCASResp,

})` OK
 */

export const postShareProV1AuthCas = (
  param: NiuniuWikiContractProApiShareV1AuthCASReq,
  params: RequestParams = {},
) =>
  httpRequest<
    DomainPWResponse & {
      data?: NiuniuWikiContractProApiShareV1AuthCASResp;
    }
  >({
    path: `/share/pro/v1/auth/cas`,
    method: "POST",
    body: param,
    type: ContentType.Json,
    format: "json",
    ...params,
  });

/**
 * @description 钉钉登录
 *
 * @tags ShareAuth
 * @name PostShareProV1AuthDingtalk
 * @summary 钉钉登录
 * @request POST:/share/pro/v1/auth/dingtalk
 * @response `200` `(DomainPWResponse & {
    data?: NiuniuWikiContractProApiShareV1AuthDingTalkResp,

})` OK
 */

export const postShareProV1AuthDingtalk = (
  param: NiuniuWikiContractProApiShareV1AuthDingTalkReq,
  params: RequestParams = {},
) =>
  httpRequest<
    DomainPWResponse & {
      data?: NiuniuWikiContractProApiShareV1AuthDingTalkResp;
    }
  >({
    path: `/share/pro/v1/auth/dingtalk`,
    method: "POST",
    body: param,
    type: ContentType.Json,
    format: "json",
    ...params,
  });

/**
 * @description 飞书登录
 *
 * @tags ShareAuth
 * @name PostShareProV1AuthFeishu
 * @summary 飞书登录
 * @request POST:/share/pro/v1/auth/feishu
 * @response `200` `(DomainPWResponse & {
    data?: NiuniuWikiContractProApiShareV1AuthFeishuResp,

})` OK
 */

export const postShareProV1AuthFeishu = (
  param: NiuniuWikiContractProApiShareV1AuthFeishuReq,
  params: RequestParams = {},
) =>
  httpRequest<
    DomainPWResponse & {
      data?: NiuniuWikiContractProApiShareV1AuthFeishuResp;
    }
  >({
    path: `/share/pro/v1/auth/feishu`,
    method: "POST",
    body: param,
    type: ContentType.Json,
    format: "json",
    ...params,
  });

/**
 * @description GitHub登录
 *
 * @tags ShareAuth
 * @name PostShareProV1AuthGithub
 * @summary GitHub登录
 * @request POST:/share/pro/v1/auth/github
 * @response `200` `(DomainPWResponse & {
    data?: NiuniuWikiContractProApiShareV1AuthGitHubResp,

})` OK
 */

export const postShareProV1AuthGithub = (
  param: NiuniuWikiContractProApiShareV1AuthGitHubReq,
  params: RequestParams = {},
) =>
  httpRequest<
    DomainPWResponse & {
      data?: NiuniuWikiContractProApiShareV1AuthGitHubResp;
    }
  >({
    path: `/share/pro/v1/auth/github`,
    method: "POST",
    body: param,
    type: ContentType.Json,
    format: "json",
    ...params,
  });

/**
 * @description AuthInfo
 *
 * @tags ShareAuth
 * @name GetShareProV1AuthInfo
 * @summary AuthInfo
 * @request GET:/share/pro/v1/auth/info
 * @response `200` `(DomainPWResponse & {
    data?: NiuniuWikiContractProApiShareV1AuthInfoResp,

})` OK
 */

export const getShareProV1AuthInfo = (params: RequestParams = {}) =>
  httpRequest<
    DomainPWResponse & {
      data?: NiuniuWikiContractProApiShareV1AuthInfoResp;
    }
  >({
    path: `/share/pro/v1/auth/info`,
    method: "GET",
    type: ContentType.Json,
    format: "json",
    ...params,
  });

/**
 * @description LDAP登录
 *
 * @tags ShareAuth
 * @name PostShareProV1AuthLdap
 * @summary LDAP登录
 * @request POST:/share/pro/v1/auth/ldap
 * @response `200` `(DomainPWResponse & {
    data?: NiuniuWikiContractProApiShareV1AuthLDAPResp,

})` OK
 */

export const postShareProV1AuthLdap = (
  param: NiuniuWikiContractProApiShareV1AuthLDAPReq,
  params: RequestParams = {},
) =>
  httpRequest<
    DomainPWResponse & {
      data?: NiuniuWikiContractProApiShareV1AuthLDAPResp;
    }
  >({
    path: `/share/pro/v1/auth/ldap`,
    method: "POST",
    body: param,
    type: ContentType.Json,
    format: "json",
    ...params,
  });

/**
 * @description 用户登出
 *
 * @tags ShareAuth
 * @name PostShareProV1AuthLogout
 * @summary 用户登出
 * @request POST:/share/pro/v1/auth/logout
 * @response `200` `(DomainPWResponse & {
    data?: NiuniuWikiContractProApiShareV1AuthLogoutResp,

})` OK
 */

export const postShareProV1AuthLogout = (params: RequestParams = {}) =>
  httpRequest<
    DomainPWResponse & {
      data?: NiuniuWikiContractProApiShareV1AuthLogoutResp;
    }
  >({
    path: `/share/pro/v1/auth/logout`,
    method: "POST",
    type: ContentType.Json,
    format: "json",
    ...params,
  });

/**
 * @description OAuth登录
 *
 * @tags ShareAuth
 * @name PostShareProV1AuthOauth
 * @summary OAuth登录
 * @request POST:/share/pro/v1/auth/oauth
 * @response `200` `(DomainPWResponse & {
    data?: NiuniuWikiContractProApiShareV1AuthOAuthResp,

})` OK
 */

export const postShareProV1AuthOauth = (
  param: NiuniuWikiContractProApiShareV1AuthOAuthReq,
  params: RequestParams = {},
) =>
  httpRequest<
    DomainPWResponse & {
      data?: NiuniuWikiContractProApiShareV1AuthOAuthResp;
    }
  >({
    path: `/share/pro/v1/auth/oauth`,
    method: "POST",
    body: param,
    type: ContentType.Json,
    format: "json",
    ...params,
  });

/**
 * @description 企业微信登录
 *
 * @tags ShareAuth
 * @name PostShareProV1AuthWecom
 * @summary 企业微信登录
 * @request POST:/share/pro/v1/auth/wecom
 * @response `200` `(DomainPWResponse & {
    data?: NiuniuWikiContractProApiShareV1AuthWecomResp,

})` OK
 */

export const postShareProV1AuthWecom = (
  param: NiuniuWikiContractProApiShareV1AuthWecomReq,
  params: RequestParams = {},
) =>
  httpRequest<
    DomainPWResponse & {
      data?: NiuniuWikiContractProApiShareV1AuthWecomResp;
    }
  >({
    path: `/share/pro/v1/auth/wecom`,
    method: "POST",
    body: param,
    type: ContentType.Json,
    format: "json",
    ...params,
  });
