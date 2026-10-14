import request from '/@/utils/request';

/**
 * https://www.ietf.org/rfc/rfc6749.txt
 * OAuth 协议 4.3.1 要求格式为 form 而不是 JSON 注意！
 */
const FORM_CONTENT_TYPE = 'application/x-www-form-urlencoded';
const BROWSER_CLIENT_ID = 'bixi-browser';

/**
 * 登录
 * @param data
 */
export const login = (data: any) => {
    return request({
        url: '/auth/oauth2/token',
        method: 'post',
        data: {...data, client_id: BROWSER_CLIENT_ID},
        headers: {
            skipToken: true,
            'Content-Type': FORM_CONTENT_TYPE,
        },
    });
};

export const loginByMobile = (mobile: any, code: any) => {
    const grant_type = 'mobile';
    const scope = 'server';

    return request({
        url: '/auth/oauth2/token',
        headers: {
            skipToken: true,
            'Content-Type': FORM_CONTENT_TYPE,
        },
        method: 'post',
        data: {mobile: mobile, code: code, grant_type, scope, client_id: BROWSER_CLIENT_ID},
    });
};

export const loginBySocial = (state: string, code: string) => {
    const grant_type = 'mobile';
    const scope = 'server';

    return request({
        url: '/auth/oauth2/token',
        headers: {
            skipToken: true,
            'Content-Type': FORM_CONTENT_TYPE,
        },
        method: 'post',
        data: {mobile: state + '@' + code, code: code, grant_type, scope, client_id: BROWSER_CLIENT_ID},
    });
};

export const sendMobileCode = (mobile: any) => {
    return request({
        url: '/admin/mobile/' + mobile,
        method: 'get',
    });
};

export const refreshTokenApi = (refresh_token: string) => {
    const grant_type = 'refresh_token';
    const scope = 'server';

    return request({
        url: '/auth/oauth2/token',
        headers: {
            skipToken: true,
            'Content-Type': FORM_CONTENT_TYPE,
        },
        method: 'post',
        data: {refresh_token, grant_type, scope, client_id: BROWSER_CLIENT_ID},
    });
};

/**
 * 获取用户信息
 */
export const getUserInfo = () => {
    return request({
        url: '/admin/user/info',
        method: 'get',
    });
};

export const logout = () => {
    return request({
        url: '/auth/token/logout',
        method: 'delete',
    });
};
