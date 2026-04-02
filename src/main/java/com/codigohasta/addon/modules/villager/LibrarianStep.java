package com.codigohasta.addon.modules.villager;

/**
 * 图书管理员自动交易与分类系统的状态机枚举
 * 适用于 Minecraft 1.21.11 架构
 */
public enum LibrarianStep {
    // ==========================================
    // 阶段 0：空闲与异常处理
    // ==========================================
    NONE,                   // 模块关闭或发生无法恢复的异常，停止一切行动
    WAIT,                   // 挂机等待（村民已下班、或全员处于补货冷却中）

    // ==========================================
    // 阶段 1：摸底勘探阶段 (Discovery Phase)
    // ==========================================
    INIT_SCAN,              // 扫描周围所有未记录数据的图书管理员工作台
    WALK_TO_UNDISCOVERED,   // 寻路走向未知的图书管理员
    OPEN_FOR_DISCOVER,      // 强行打开交易界面，读取 1.21.11 组件(Components)记录附魔数据

    // ==========================================
    // 阶段 2：智能物流补给阶段 (Supply Phase)
    // ==========================================
    CHECK_SUPPLY,           // 核心判定节点：计算下一个目标的精准进货量（还差多少绿宝石和普通书）
    GOTO_EMERALD,           // 寻路走向绿宝石木桶
    TAKE_EMERALD,           // 拿取精准数量的绿宝石
    GOTO_BASE_BOOK,         // 寻路走向普通书容器
    TAKE_BASE_BOOK,         // 拿取精准数量的普通书（minecraft:book）

    // ==========================================
    // 阶段 3：核心交易阶段 (Trading Phase)
    // ==========================================
    NEXT_LIBRARIAN,         // 寻找下一个符合玩家需求、且不在冷却中的图书管理员
    WALKING_TO_VILLAGER,    // 寻路走向目标村民
    OPEN_TRADE,             // 射线检测并打开交易界面
    EXECUTE_TRADE,          // 瞬间扫货：发包选中交易 -> 延迟 -> Shift-Click 结果槽 -> 判定售罄
    CLOSE_SCREEN_AND_NEXT,  // 安全关闭界面 (发包同步以防幽灵物品)，并切回 CHECK_SUPPLY 或分类

    // ==========================================
    // 阶段 4：分类与卸货阶段 (Sorting & Dumping Phase)
    // ==========================================
    GOTO_SORT_AREA,         // 满载附魔书，寻路走向预设的分类潜影盒区域
    SORT_BOOKS,             // 遍历背包内的附魔书，Shift-Click 存入对应颜色的潜影盒

    // ==========================================
    // 阶段 5：满盒自动替换微操 (Shulker Box Replacement)
    // ==========================================
    HANDLE_FULL_BOX,        // 检测到对应颜色的潜影盒已满，准备启动换盒程序
    BREAK_FULL_BOX,         // 对准潜影盒发送挖掘数据包，等待掉落并吸入背包
    GOTO_DUMP_CHEST,        // 寻路走向“满盒回收大箱子”
    DUMP_FULL_BOX,          // 将背包中带有 NBT 的满潜影盒存入大箱子
    GOTO_EMPTY_BOX_CHEST,   // 寻路走向“空盒补给区”
    TAKE_EMPTY_BOX,         // 提取一个与刚才挖掉颜色相同的空潜影盒
    GOTO_PLACE_POS,         // 回到刚才挖掉潜影盒的坐标
    PLACE_NEW_BOX           // 调用 HeBlockUtils 放下新潜影盒，并切回 SORT_BOOKS 继续分类
}