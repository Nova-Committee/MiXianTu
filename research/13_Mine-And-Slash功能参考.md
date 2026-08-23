# Mine & Slash 功能参考整理

本文记录对 `E:\Java\Mine-And-Slash-Rework-1.20-Forge` 的功能层阅读结果。

## 阅读范围

- 源项目：Mine & Slash Rework
- Minecraft：1.20.1
- Forge：47.1.43
- 本文只关注玩家可感知的功能、界面和模块边界，不迁移其底层 Gson 数据库、网络封装或旧版 Forge API。
- MiXianTu 当前定位仍然是修仙框架：不提供具体生物、物品数值、剧情和美术内容。

## 结论摘要

最值得借鉴的功能是：

1. 资源条、技能快捷栏和吟唱条的组合式 HUD。
2. 主动技能、被动技能、命中触发、受击触发和周期技能的统一生命周期。
3. 灵气工作台类似的持久化输入、配方锁定和运行状态。
4. 品质、装备属性来源、插槽和 Tooltip 的展示方式。
5. 掉落蓝图、奖励箱和地图词缀的功能拆分。
6. 天赋树/分支成长界面。

不建议直接移植源项目的职业、地图赛季、具体怪物数值和 Mine & Slash 专用平衡体系。

## 功能模块对照

### 1. HUD 与界面

**来源：**

- `src/main/java/com/robertx22/mine_and_slash/gui/overlays/BarGuiType.java`
- `src/main/java/com/robertx22/mine_and_slash/gui/overlays/bar_overlays/types/RPGGuiOverlay.java`
- `src/main/java/com/robertx22/mine_and_slash/gui/overlays/bar_overlays/types/MineAndSlashBars.java`
- `src/main/java/com/robertx22/mine_and_slash/gui/overlays/spell_hotbar/`
- `src/main/java/com/robertx22/mine_and_slash/gui/overlays/spell_cast_bar/`
- `src/main/java/com/robertx22/mine_and_slash/gui/screens/character_screen/`
- `src/main/java/com/robertx22/mine_and_slash/gui/screens/stat_gui/`
- `src/main/java/com/robertx22/mine_and_slash/gui/screens/skill_tree/`
- `src/main/java/com/robertx22/mine_and_slash/gui/screens/spell/`

**已有功能：**

- 生命、法力、能量、魔法护盾、经验、饥饿和空气条。
- 资源条左右布局、图标、满值隐藏和顶部/侧边显示。
- 技能快捷栏、技能图标、冷却显示和快捷键。
- 技能吟唱进度条。
- 角色属性界面、属性来源界面、装备界面。
- 技能树、节点连线、技能学校和点数界面。
- 地图、宝石和技能物品相关界面。

**对 MiXianTu 的建议：**

- **高优先级：**继续完善当前 `resource_bar`，吸收其左右列、满值隐藏、图标和文本布局思路。
- **高优先级：**为 `Ability` 预留技能快捷栏和吟唱条显示。
- **中优先级：**把角色面板改造成境界、修为、灵根、体质和灵气分布面板。
- **中优先级：**将技能树思路用于功法分支或宗门传承。
- **低优先级：**内置 Wiki、地图面板和技能学校界面暂时不进入核心框架。

MiXianTu 只应借鉴交互和信息层级，不能直接复制 1.20.1 GUI 实现。

### 2. 技能与战斗

**来源：**

- `src/main/java/com/robertx22/mine_and_slash/database/data/spells/components/Spell.java`
- `src/main/java/com/robertx22/mine_and_slash/database/data/spells/components/SpellConfiguration.java`
- `src/main/java/com/robertx22/mine_and_slash/database/data/spells/components/ComponentPart.java`
- `src/main/java/com/robertx22/mine_and_slash/database/data/spells/components/actions/`
- `src/main/java/com/robertx22/mine_and_slash/database/data/spells/components/conditions/`
- `src/main/java/com/robertx22/mine_and_slash/database/data/spells/components/selectors/`

**已有功能：**

- 主动释放、攻击触发、命中触发、受击触发、周期触发和过期触发。
- AOE、前方、施法者、射线等目标选择方式。
- 伤害、药水、击退、推开、传送、投射物和召唤。
- 粒子、声音、动画等表现行为。
- 冷却、蓄力、吟唱、多段释放、武器限制和资源消耗。
- 支持宝石对技能进行额外修改。

**对 MiXianTu 的建议：**

直接借鉴下面的功能流水线：

```text
触发事件 -> 通用条件 -> 目标选择 -> 目标条件 -> 行为执行 -> 结束/冷却
```

当前已有 `Action`、`Condition`、`Ability`，因此只需要继续补齐：

- 目标选择器；
- 命中、受击、攻击、tick、结束等触发点；
- 持续引导和取消逻辑；
- 对每个目标独立执行的行为；
- 技能快捷栏与吟唱条的客户端显示。

不建议将每种技能写成独立 Java 类；复杂差异应继续使用固有行为类型 + 数据包参数表达。

### 3. 装备、品质与属性

**来源：**

- `src/main/java/com/robertx22/mine_and_slash/database/data/rarities/`
- `src/main/java/com/robertx22/mine_and_slash/database/data/affixes/`
- `src/main/java/com/robertx22/mine_and_slash/database/data/gems/`
- `src/main/java/com/robertx22/mine_and_slash/database/data/runes/`
- `src/main/java/com/robertx22/mine_and_slash/database/data/runewords/`
- `src/main/java/com/robertx22/mine_and_slash/database/data/unique_items/`
- `src/main/java/com/robertx22/mine_and_slash/database/data/gear_types/`
- `src/main/java/com/robertx22/mine_and_slash/loot/blueprints/`

**已有功能：**

- 装备部位和武器类型。
- 品质、颜色、品质顺序和稀有度。
- 前缀、后缀、隐式属性和随机数值区间。
- 插槽、宝石、符文和符文之语。
- 独特装备、装备等级、潜能和腐化。
- 装备属性来源和详细 Tooltip。

**对 MiXianTu 的建议：**

- **已适合：**当前 `item_quality`、`weapon_binding`、`pill_binding`、`artifact` 的品质和 Tooltip。
- **可预留：**插槽、符文、装备附加属性和独特物品。
- **暂不加入：**Mine & Slash 的具体装备数值、职业限制和专用掉落平衡。

如果以后加入符文或附加属性，应优先复用当前 `Modifier`、`Condition` 和 Item Component，而不是照搬源项目的装备 NBT 结构。

### 4. 制作、炼制与职业工作台

**来源：**

- `src/main/java/com/robertx22/mine_and_slash/database/data/profession/ProfessionRecipe.java`
- `src/main/java/com/robertx22/mine_and_slash/database/data/profession/ProfessionBlockEntity.java`
- `src/main/java/com/robertx22/mine_and_slash/database/data/profession/screen/CraftingStationMenu.java`
- `src/main/java/com/robertx22/mine_and_slash/database/data/profession/Crafting_State.java`

**已有功能：**

- 职业等级和职业经验。
- 分级配方、等级限制和配方难度。
- 输入材料检查、输出槽和材料消耗。
- 配方锁定、工作台状态和持久化库存。
- 关闭 GUI 后重新打开仍保留输入。
- 制作失败原因、产出经验和额外产出。
- 分解/回收工作流。

**对 MiXianTu 的建议：**

这是当前灵气工作台、炼丹和锻造最有价值的参考模块。重点吸收：

- `STOPPED / IDLE / ACTIVE` 三态；
- 配方锁定后只允许匹配当前配方；
- 工作台输入在 BlockEntity 中持久化；
- 输出槽满时阻止完成或将物品安全掉落；
- 配方检查返回可显示给玩家的失败原因。

职业等级本身可以不移植，后续可替换成境界、熟练度或功法等级。

### 5. 地图与秘境

**来源：**

- `src/main/java/com/robertx22/mine_and_slash/maps/MapItem.java`
- `src/main/java/com/robertx22/mine_and_slash/maps/MapItemData.java`
- `src/main/java/com/robertx22/mine_and_slash/maps/MapData.java`
- `src/main/java/com/robertx22/mine_and_slash/maps/MapAffixData.java`
- `src/main/java/com/robertx22/mine_and_slash/maps/MapEvents.java`
- `src/main/java/com/robertx22/mine_and_slash/maps/LeagueData.java`

**已有功能：**

- 地图物品、地图等级和地图词缀。
- 区域等级和地图难度。
- 地图生命次数。
- 地图事件、赛季/联盟事件和受影响实体。
- 地图维度和副本流程。

**对 MiXianTu 的建议：**

- 可借鉴“秘境规则集”和“秘境词缀”概念。
- 可将灵气浓度、阵法强度、天劫修正和人数限制作为秘境规则。
- 当前先实现 `realm_instance` 的进出、时限和成员管理即可。
- 不建议现在加入完整地图物品、赛季和联盟系统。

### 6. 掉落与奖励

**来源：**

- `src/main/java/com/robertx22/mine_and_slash/loot/`
- `src/main/java/com/robertx22/mine_and_slash/loot/blueprints/`
- `src/main/java/com/robertx22/mine_and_slash/loot/generators/`
- `src/main/java/com/robertx22/mine_and_slash/database/data/loot_chest/`

**已有功能：**

- 装备、宝石、符文、货币、地图和奖励箱掉落。
- 掉落蓝图和掉落修正。
- 按品质、等级和权重生成结果。
- 不同类型奖励箱拥有独立生成器。

**对 MiXianTu 的建议：**

可借鉴以下流程：

```text
掉落来源 -> 掉落类型 -> 等级/品质限制 -> 权重选择 -> 生成物品 -> 奖励修正
```

这适合灵石矿脉、秘境奖励箱、锻造材料、功法书和稀有灵宝。具体物品和数值仍交给数据包或 KubeJS。

### 7. 角色成长与天赋

**来源：**

- `src/main/java/com/robertx22/mine_and_slash/database/data/talent_tree/`
- `src/main/java/com/robertx22/mine_and_slash/database/data/perks/`
- `src/main/java/com/robertx22/mine_and_slash/database/data/spell_school/`
- `src/main/java/com/robertx22/mine_and_slash/gui/screens/skill_tree/`
- `src/main/java/com/robertx22/mine_and_slash/gui/screens/spell/`

**已有功能：**

- 天赋节点、节点连线和前置关系。
- 天赋点和分支成长。
- 技能学校、职业分支和技能等级。

**对 MiXianTu 的建议：**

- 可用于功法分支、宗门传承、体质成长和特殊修炼路线。
- 当前境界系统是线性的，若未来需要非线性功法成长，可以单独增加 `technique_tree`。
- 不需要移植职业/技能学校概念本身。

### 8. 生物、召唤与宠物

**来源：**

- `src/main/java/com/robertx22/mine_and_slash/database/data/EntityConfig.java`
- `src/main/java/com/robertx22/mine_and_slash/database/data/rarities/MobRarity.java`
- `src/main/java/com/robertx22/mine_and_slash/database/data/mob_affixes/`
- `src/main/java/com/robertx22/mine_and_slash/database/data/spells/summons/`

**已有功能：**

- 实体等级和属性倍率。
- 生物稀有度和词缀。
- 召唤物、宠物和特殊实体。

**对 MiXianTu 的建议：**

只借鉴“实体绑定配置”和“召唤行为”。本模组不负责具体妖兽、Boss、生命值和攻击力数值，复杂模型与动画使用 GeckoLib 或内容模组处理。

### 9. 视觉、动画与特效

**来源：**

- `src/main/java/com/robertx22/mine_and_slash/database/data/spells/components/actions/vanity/`
- `src/main/java/com/robertx22/mine_and_slash/database/data/spells/entities/renders/`
- `src/main/java/com/robertx22/mine_and_slash/a_libraries/player_animations/`
- `src/main/java/com/robertx22/mine_and_slash/gui/overlays/`

**已有功能：**

- 施法动画、技能粒子、声音和投射物渲染。
- 召唤物模型和渲染。
- 品质发光和技能图标。
- 资源条、技能条和 HUD 特效。

**对 MiXianTu 的建议：**

- 当前粒子、灵气射线、ResourceBar 和 GeckoLib 方向已经足够。
- 可借鉴技能表现的配置方式：粒子、声音、动画作为行为的一部分。
- 不移植旧版玩家动画库和渲染实现。

### 10. 兼容与外围功能

**源项目涉及：**

- Curios 装备槽位；
- JEI 配方展示；
- Player Animator；
- Dungeon Realm；
- The Harvest；
- 地图联盟和队伍相关功能。

**对 MiXianTu 的建议：**

- Curios、JEI、Jade 继续使用当前项目已有兼容层。
- GeckoLib 负责模型和骨骼动画。
- FTB Quests/Teams/Chunks 等通用功能不在 MiXianTu 重复实现。
- 只为实际存在的前置模组增加兼容，不移植源项目的联盟、赛季和职业依赖。

## 明确不移植的源项目内容

- Mine & Slash 的具体怪物、Boss、装备和技能数值。
- 以职业和技能宝石为核心的成长体系。
- 地图赛季、联盟和专用地图物品。
- 源项目的完整装备随机生成平衡。
- 源项目的旧版 Forge GUI、网络包和渲染实现。
- 依赖 `library_of_exile` 的自定义数据库框架。

## 对 MiXianTu 的功能优先级

### 高优先级

- ResourceBar、技能快捷栏、吟唱条。
- Action/Condition/目标选择组合。
- 灵气工作台的持久化制作状态。
- 配方失败原因和 JEI/Jade 展示。
- 品质、Tooltip 和物品属性来源。

### 中优先级

- 功法/体质分支树。
- 秘境规则和秘境词缀。
- 奖励箱和掉落蓝图。
- 插槽、符文和灵宝附加效果。

### 预留

- 地图物品和赛季事件。
- 完整职业系统。
- 妖兽稀有度和词缀。
- 游戏内 Wiki、地图 GUI 和大型角色面板。

## 来源说明

本文所有 Mine & Slash 功能均来自本地项目：

`E:\Java\Mine-And-Slash-Rework-1.20-Forge`

具体来源路径已在每个模块下列出。本文是功能参考，不表示这些功能已经在 MiXianTu 中实现，也不改变 MiXianTu “只提供框架、不提供具体内容数值”的定位。
