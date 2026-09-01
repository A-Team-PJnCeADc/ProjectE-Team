# ProjectE-Team

> "一个人的 EMC 终会枯竭,但一个团队的传承永不消逝。"

ProjectE-Team 是经典等价交换模组 **[ProjectE](https://www.mcmod.cn/class/354.html)** 的 1.7.10 附属模组,为多人游戏添加**团队系统**:队员之间共享 EMC 与炼金术知识(转化知识),让团队协作真正成为等价交换的一部分。

本模组**不修改 ProjectE 的任何源码**——通过注册 ProjectE `TransmutationProps` 的多态子类,在玩家加入团队后将 EMC/知识的读写透明地重定向到团队共享池,因此对 ProjectE 与第三方附属模组兼容性较好。

## Progress

Developing

## 核心机制

- **团队共享**:玩家加入(创建)团队后,EMC 与炼金术知识归属团队共享池;玩家个人的 EMC/知识在加入时冻结,**离开团队后自动还原**
- **按需开关**:EMC 共享与知识共享可分别开关(默认均开启),由队长控制
- **自动管理**:队长离开时队长自动转让给第一位成员;最后一人离开时团队自动解散
- **数据持久化**:团队数据存入世界存档(WorldSavedData),重启服务器不丢失
- **客户端零改动**:通过 ProjectE 自身的同步机制推送生效数据,客户端无需安装额外逻辑

## 游戏指令

所有指令均可在游戏内直接使用,Tab 补全可用:

- `/projecte_team` — 显示指令帮助
- `/projecte_team create [名字]` — 创建团队并命名(缺省时默认 `<玩家名> team`)
- `/projecte_team invite <玩家>` — 邀请玩家加入团队(无团队时自动创建,需对方确认)
- `/projecte_team accept` — 接受待处理的团队邀请
- `/projecte_team decline` — 拒绝待处理的团队邀请
- `/projecte_team members` — 列出团队成员(标记队长与离线成员)
- `/projecte_team kick <玩家>` — 踢出成员(仅队长)
- `/projecte_team leave` — 离开团队(队长离开时自动转让/解散)
- `/projecte_team transfer_owner <玩家>` — 转让队长(仅队长)
- `/projecte_team settings share_emc <true|false>` — 设置 EMC 共享(默认: true)
- `/projecte_team settings share_knowledge <true|false>` — 设置知识共享(默认: true)

> 支持中英文双语指令反馈(zh_CN / en_US)。

## 已知限制与计划

- [ ] **改用网络包同步**
- [ ] **gui内立即同步**
- [ ] **离线成员管理**:kick / transfer_owner 目标需在线(离线玩家无法按游戏名定位)


## 依赖

- **Minecraft 1.7.10** / **Forge 10.13.4.1614**
- **[ProjectE 1.7.10 (PE1.10.1)]([https://www.curseforge.com/minecraft/mc-mods/projecte](https://github.com/sinkillerj/ProjectE/tree/MC17))**
- 构建环境:Java 8+(GTNH 约定构建脚本,支持 jabel 现代语法)

## 构建与开发

```bash
# 克隆仓库
git clone git@github.com:A-Team-PJnCeADc/ProjectE-Team.git
cd ProjectE-Team

# 构建模组
./gradlew build

# 运行客户端(需先安装 ProjectE)
./gradlew runClient
```

构建产物位于 `build/libs/`。将 ProjectE 与 ProjectE-Team 的 jar 放入 `mods/` 目录即可游玩。

## 贡献

欢迎提交 Pull Request 或 Issue。在提交前请确保:

1. 构建没有错误(`./gradlew build` 通过,含 spotless 格式检查)
2. 通用代码放在 `src/main/`,客户端专用代码放在客户端代理(proxy)中
3. 指令文案同步更新 `src/main/resources/assets/projecteteam/lang/` 下的中英文语言文件

## 致谢

- 所有参与开发,测试,建议的人员

## License

<a href="https://github.com/A-Team-PJnCeADc/ProjectE-Team">ProjectE-Team</a> © 2026 by <a href="https://github.com/A-Team-PJnCeADc">A-Team-PJnCeADc</a> is licensed under <a href="https://creativecommons.org/licenses/by-nc-sa/4.0/">Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International</a>
