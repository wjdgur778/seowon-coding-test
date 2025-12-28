package com.seowon.coding.domain.model;


import lombok.Builder;

import java.util.List;
import java.util.Set;

class PermissionChecker {

    /**
     * TODO #7: 코드를 최적화하세요
     * 테스트 코드`PermissionCheckerTest`를 활용하시면 리펙토링에 도움이 됩니다.
     */
    public static boolean hasPermission(
            String userId,
            String targetResource,
            String targetAction,
            List<User> users,
            List<UserGroup> groups,
            List<Policy> policies
    ) {
        //시간이 부족해 아이디어만 적어보겠습니다.
        //              List<User> users,
        //            List<UserGroup> groups,
        //            List<Policy> policies
        //   이렇게 리스트로 되어있는 것들을 hashset으로 변환해서 contain을 활용하면 좀더 빠를것같습니다.
            for (User user : users) {
                if (user.id.equals(userId)) {
                    for (UserGroup group : groups) {
                         for (String groupId : user.groupIds) {
                            if (group.id.equals(groupId)) {
                                for (String policyId : group.policyIds) {
                                    for (Policy policy : policies) {
                                        if (policy.id.equals(policyId)) {
                                            for (Statement statement : policy.statements) {
                                                if (statement.actions.contains(targetAction) &&
                                                        statement.resources.contains(targetResource)) {
                                                    return true;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            return false;

    }

class User {
    String id;
    List<String> groupIds;

    public User(String id, List<String> groupIds) {
        this.id = id;
        this.groupIds = groupIds;
    }
}

class UserGroup {
    String id;
    List<String> policyIds;

    public UserGroup(String id, List<String> policyIds) {
        this.id = id;
        this.policyIds = policyIds;
    }
}

class Policy {
    String id;
    List<Statement> statements;

    public Policy(String id, List<Statement> statements) {
        this.id = id;
        this.statements = statements;
    }
}

class Statement {
    List<String> actions;
    List<String> resources;

    @Builder
    public Statement(List<String> actions, List<String> resources) {
        this.actions = actions;
        this.resources = resources;
    }
}