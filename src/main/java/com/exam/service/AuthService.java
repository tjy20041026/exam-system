package com.exam.service;

import com.exam.dto.LoginDTO;
import com.exam.vo.LoginVO;
import com.exam.vo.UserVO;

public interface AuthService {

    /** 登录，成功返回 token 和用户信息 */
    LoginVO login(LoginDTO dto);

    /** 当前登录用户。这里重新查库而不是用 token 里解出来的信息 —— token 签发后就改不了，里面是那一刻的角色和状态快照。 */
    UserVO currentUser();
}
