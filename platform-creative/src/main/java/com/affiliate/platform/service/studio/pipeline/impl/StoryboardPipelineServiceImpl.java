package com.affiliate.platform.service.studio.pipeline.impl;

import com.affiliate.platform.domain.studio.*;
import com.affiliate.platform.service.studio.pipeline.StoryboardPipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 6 套行业分镜管线服务实现
 */
@Service
public class StoryboardPipelineServiceImpl implements StoryboardPipelineService {

    private static final Logger log = LoggerFactory.getLogger(StoryboardPipelineServiceImpl.class);

    @Override
    public AdTypePipeline resolvePipeline(String adType) {
        return AdTypePipeline.fromAdType(adType);
    }

    @Override
    public String buildPipelineSystemPrompt(AdTypePipeline pipeline) {
        return switch (pipeline) {
            case ECOMMERCE_SHOWCASE -> """
                    你是一名顶级效果类短视频电商编导。
                    【管线特点】：电商口播带货，痛点暴击 -> 爆款实物特写 -> 原料成分与机理背书 -> 价格优惠爆点 -> CTA逼单。
                    【硬性约束】：
                    1. 镜头1必须为黄金3秒Hook，时长>=3秒。
                    2. 单镜头时长严格在 3~15 秒之间。
                    3. shot_prompt 字符串末尾必须包含明确的「收尾画面：xxx」。
                    4. voiceover_text 必须纯净可朗读，严禁带有“旁白：”或心理描写等前缀。
                    5. subtitle_text 每行限制在15字以内，给出需要高亮变色的关键词数组。
                    """;
            case GAMING_INTERACTIVE -> """
                    你是一名顶尖 IAA 小游戏买量广告编导。
                    【管线特点】：IAA小游戏试玩，神操作开局 -> 意外失误/笨拙操作导致死局 -> 濒死绝望求生 -> 激起胜负欲引导点击立即自救。
                    【硬性约束】：首镜必须有极高视觉张力，单镜3~15秒，Prompt末尾必须带「收尾画面：xxx」，字幕精简有力。
                    """;
            case DRAMA_HOOK_TWIST -> """
                    你是一名爆款竖屏短剧反转编导。
                    【管线特点】：短剧反转，家庭/职场冲突爆发点卡点 -> 绝境爆发/隐藏神豪身份反转 -> 高光爽感打脸时刻 -> 悬念卡点引流下集。
                    【硬性约束】：单镜3~15秒，Prompt末尾必须带「收尾画面：xxx」，高能反转节点必须带有清晰过渡说明。
                    """;
            case BRAND_CINEMATIC -> """
                    你是一名国际 4A 广告公司电影级品牌形象导演。
                    【管线特点】：品牌形象故事，电影级构图调色 -> 真实人物痛点心声 -> 品牌科技与温情陪伴 -> 精神共鸣与情感升华。
                    【硬性约束】：单镜3~15秒，强调景别与运镜（推拉摇移升降），Prompt末尾带「收尾画面：xxx」。
                    """;
            case FLASH_SALE_PROMO -> """
                    你是一名大促限时狂欢促销编导。
                    【管线特点】：促销强刺激，红黄色系大字报冲击 -> 疯狂砍价破价 -> 赠品堆叠如山 -> 库存倒计时紧急逼单。
                    【硬性约束】：单镜3~15秒，节奏明快紧凑，BPM 120+，Prompt末尾带「收尾画面：xxx」。
                    """;
            case LOCAL_LIFESTYLE -> """
                    你是一名百万粉本地生活美食探店主理人。
                    【管线特点】：本地探店生活，第一视角手持推门进店 -> 招牌爆品爆浆拉丝大特写 -> 真实性价比体验与省钱算账 -> 闭眼冲限时团购。
                    【硬性约束】：单镜3~15秒，强调食物质感与生活气，Prompt末尾带「收尾画面：xxx」。
                    """;
        };
    }

    @Override
    public List<StudioAsset> analyzeAssets(StudioProject project) {
        String brief = (project.brief() != null ? project.brief() : "") + " " + (project.title() != null ? project.title() : "");
        AdTypePipeline pipeline = resolvePipeline(project.adType());

        List<StudioAsset> assets = new ArrayList<>();

        // 1. 拆解角色人物
        String charName = switch (pipeline) {
            case ECOMMERCE_SHOWCASE -> "职场知性女性主播";
            case GAMING_INTERACTIVE -> "元气年轻游戏主播";
            case DRAMA_HOOK_TWIST -> "逆袭商业女霸总";
            case BRAND_CINEMATIC -> "现代都市探索者";
            case FLASH_SALE_PROMO -> "高能砍价达人";
            case LOCAL_LIFESTYLE -> "美食探店主理人";
        };
        String charPrompt = String.format("A realistic East Asian portrait of %s, photorealistic, 8k resolution, clean neutral lighting, studio portrait, white background", charName);
        assets.add(StudioAsset.create(
                project.tenantId(),
                project.id(),
                StudioAssetType.CHARACTER,
                charName,
                "主视觉出镜角色，配合口播与分镜情节推进",
                Map.of("prompt", charPrompt, "style", project.creativeStyle())
        ));

        // 2. 拆解核心商品/标的物
        String prodName = "推广核心标的物";
        if (brief.contains("脱发") || brief.contains("洗发")) {
            prodName = "防脱固发精华生发液";
        } else if (brief.contains("游戏") || brief.contains("玩法")) {
            prodName = "益智解密小游戏核心关卡道具";
        } else if (brief.contains("咖啡") || brief.contains("餐饮")) {
            prodName = "招牌厚乳拿铁咖啡";
        }

        String prodPrompt = String.format("Commercial product photography of %s, clean product packaging, soft commercial studio light, 8k, isolated on light background", prodName);
        assets.add(StudioAsset.create(
                project.tenantId(),
                project.id(),
                StudioAssetType.PRODUCT,
                prodName,
                "广告推广的核心产品，分镜中多次特写展示",
                Map.of("prompt", prodPrompt, "aspectRatio", project.aspectRatio())
        ));

        return assets;
    }

    @Override
    public List<StudioScene> generateStoryboardScenes(StudioProject project) {
        AdTypePipeline pipeline = resolvePipeline(project.adType());
        List<StudioScene> scenes = new ArrayList<>();

        switch (pipeline) {
            case ECOMMERCE_SHOWCASE -> generateEcommerceScenes(project, scenes);
            case GAMING_INTERACTIVE -> generateGamingScenes(project, scenes);
            case DRAMA_HOOK_TWIST -> generateDramaScenes(project, scenes);
            case BRAND_CINEMATIC -> generateBrandScenes(project, scenes);
            case FLASH_SALE_PROMO -> generatePromoScenes(project, scenes);
            case LOCAL_LIFESTYLE -> generateLocalLifeScenes(project, scenes);
        }

        return scenes;
    }

    private void generateEcommerceScenes(StudioProject project, List<StudioScene> scenes) {
        // Scene 1: 黄金3秒 Hook 抛出痛点
        scenes.add(StudioScene.create(
                project.tenantId(), project.id(), 1, 4,
                "特写镜头，一个年轻白领女性在梳妆镜前看着梳子上大把脱落的发丝，表情焦虑挫败。暖黄色室内侧光。镜头微移推近。收尾画面：女性抬头看着镜子，眼神充满急迫渴望。",
                "每天洗头掉一把，发际线越来越高？别再乱花冤枉钱了！",
                "温暖女声", "normal",
                "每天洗头大把掉？发际线告急！",
                List.of("大把掉", "发际线"),
                "轻电子流行 BPM110 从低沉逐渐走向明朗",
                TransitionType.CHAIN_EXTEND, "上一镜收尾画面作为本镜起始输入，角色动作连续延展"
        ));

        // Scene 2: 核心产品与成分特写
        scenes.add(StudioScene.create(
                project.tenantId(), project.id(), 2, 5,
                "超高清微距镜头，晶莹剔透的精华液滴落在一缕健康强韧的发丝上，水滴四溅散发微光，金色微胶囊分子渗透毛囊。收尾画面：发根毛囊被金色光环包裹，展现吸收完毕的强韧状态。",
                "看看这款研发3年的固发黑科技，高纯度活性肽直接直达发根毛囊！",
                "温暖女声", "normal",
                "研发3年黑科技！直达发根毛囊",
                List.of("黑科技", "发根毛囊"),
                "轻快节奏鼓点上行",
                TransitionType.MATCH_CUT, "微观毛囊与真实使用效果匹配切入"
        ));

        // Scene 3: 真人实测前后对比
        scenes.add(StudioScene.create(
                project.tenantId(), project.id(), 3, 5,
                "中景镜头，女主播手持产品微笑展示，侧分刘海露出浓密健康的小碎发，阳光斜照在发丝上呈现自然光泽。收尾画面：主播侧身对镜头自信微笑并举起产品展示瓶身。",
                "我自己连续打卡28天，原来露头皮的地方现在全是密密麻麻的小绒毛！",
                "温暖女声", "normal",
                "实测28天！冒出浓密健康小绒毛",
                List.of("28天", "小绒毛"),
                "明朗愉悦的轻音乐高潮",
                TransitionType.SAME_SCENE, "同场景主播手势动作推进"
        ));

        // Scene 4: 信任背书与限时优惠
        scenes.add(StudioScene.create(
                project.tenantId(), project.id(), 4, 4,
                "快节奏卡点镜头，权威质检报告盖章特写，随后切到精美礼盒包装与丰富的买一送一赠品排布。收尾画面：价格标签被红笔重重划掉，浮现限时特惠红底黄字折扣价。",
                "三甲医院临床测试认证，今天品牌大促买一送二，顺丰包邮到家！",
                "热烈女声", "fast",
                "三甲临床认证！买一送二顺丰包邮",
                List.of("临床认证", "买一送二"),
                "欢快急促的倒计时节拍",
                TransitionType.CHAIN_EXTEND, "赠品包装平滑过渡到行动号召"
        ));

        // Scene 5: 最终行动呼吁 (CTA)
        scenes.add(StudioScene.create(
                project.tenantId(), project.id(), 5, 4,
                "全景特写，主播手持产品指向屏幕正下方，下方动态弹出红色发光「点击下方链接立即抢购」浮层按钮，金币粒子特效飘散。收尾画面：画面定格在产品与醒目的下载/购买箭头。",
                "库存只剩最后300套，点击视频下方链接，抓紧抢购体验！",
                "热烈女声", "normal",
                "最后300套！戳左下角抢购",
                List.of("最后300套", "抢购"),
                "短促有力的定音收尾",
                TransitionType.HARD_CUT, "最终固定帧定格"
        ));
    }

    private void generateGamingScenes(StudioProject project, List<StudioScene> scenes) {
        scenes.add(StudioScene.create(
                project.tenantId(), project.id(), 1, 4,
                "第一视角手机屏幕录屏，超华丽的满屏三消连击爆破，炫彩光效与COMBO 99数字跳跃。收尾画面：最后一块积木即将落入陷阱区。",
                "以为这关能轻松拿下？99%的人都在这一步翻车了！",
                "幽默男声", "fast",
                "99%的人都翻车！你能通关吗？",
                List.of("翻车", "通关"),
                "激昂紧张的电子小游戏BGM",
                TransitionType.CHAIN_EXTEND, "连击画面失误骤停"
        ));
        scenes.add(StudioScene.create(
                project.tenantId(), project.id(), 2, 5,
                "特写镜头，红色感叹号闪烁，熔岩正在步步逼近主角小人，小人满头大汗呼救，只有一根钥匙可以拔出。收尾画面：手指错误地拔动了错误插销，大水瞬间浇灭希望。",
                "天哪！拔错插销了！水火倒灌，小人要被淹没了，快来救救他！",
                "幽默男声", "fast",
                "哎呀拔错了！小人濒死急救",
                List.of("拔错", "急救"),
                "急促心跳声与警报音效",
                TransitionType.HARD_CUT, "急转直下切入正确解法"
        ));
        scenes.add(StudioScene.create(
                project.tenantId(), project.id(), 3, 5,
                "屏幕展示正确通关路径，华丽的金币宝箱喷涌而出，过关结算界面弹出「智商高达160」徽章。收尾画面：手势图标疯狂指引下方的「立即试玩」按钮。",
                "原来只要先拉蓝色插销！不服来战，点击下方免下载立即开玩！",
                "热烈男声", "normal",
                "免下载秒开玩！测测你的智商",
                List.of("免下载", "秒开玩"),
                "胜利通关号角与欢呼音效",
                TransitionType.HARD_CUT, "定格在试玩链接"
        ));
    }

    private void generateDramaScenes(StudioProject project, List<StudioScene> scenes) {
        scenes.add(StudioScene.create(
                project.tenantId(), project.id(), 1, 4,
                "豪华宴会厅，身穿地摊货的女主被刻薄亲戚围攻嘲讽，一杯红酒迎面泼下。收尾画面：女主缓缓抬手擦掉酒渍，嘴角勾起一丝冷冽笑意。",
                "三年隐忍，你们真以为我只是个寄人篱下的穷丫头？",
                "冷峻女声", "slow",
                "三年隐忍！真以为我是穷丫头？",
                List.of("三年隐忍", "穷丫头"),
                "大提琴低沉压抑旋律",
                TransitionType.CHAIN_EXTEND, "女主冷笑延伸至保镖破门"
        ));
        scenes.add(StudioScene.create(
                project.tenantId(), project.id(), 2, 5,
                "大门轰然踹开，十位黑衣保镖分列两侧，首富管家手捧纯金黑卡单膝跪地。收尾画面：亲戚们面色煞白，手中酒杯滑落摔得粉碎。",
                "恭迎大小姐回归！顾氏全球万亿资产，请您即刻接管！",
                "威严男声", "normal",
                "恭迎大小姐回归！接管万亿资产",
                List.of("大小姐", "万亿资产"),
                "交响乐管弦齐鸣高潮爆发",
                TransitionType.HARD_CUT, "高光卡点切入"
        ));
        scenes.add(StudioScene.create(
                project.tenantId(), project.id(), 3, 4,
                "女主佩戴璀璨钻石皇冠昂首走上主位，身后大屏幕浮现各大商业寡头臣服画面。收尾画面：女主居高临下注视镜头，画面戛然而止浮现卡点倒计时。",
                "当年夺我骨髓的仇，今天一个都别想跑！点击下方看全集大反转！",
                "冷峻女声", "fast",
                "血债血偿！戳下方看高爽大结局",
                List.of("血债血偿", "看高爽全集"),
                "剧烈的心跳卡点收尾",
                TransitionType.HARD_CUT, "引流卡点"
        ));
    }

    private void generateBrandScenes(StudioProject project, List<StudioScene> scenes) {
        scenes.add(StudioScene.create(
                project.tenantId(), project.id(), 1, 5,
                "广角大景深航拍，清晨第一缕阳光洒在薄雾笼罩的苍翠茶山，露珠从嫩绿茶叶尖端滑落。收尾画面：茶农饱经风霜但满带笑意的手掌轻抚嫩叶。",
                "每一片茶叶的苏醒，都值得被大自然与匠人倾心对待。",
                "沉稳男声", "slow",
                "每一片茶叶的苏醒，源自匠心倾注",
                List.of("自然", "匠心"),
                "空灵唯美的钢琴与大自然白噪音",
                TransitionType.CHAIN_EXTEND, "晨光过渡至现代化烘焙工坊"
        ));
        scenes.add(StudioScene.create(
                project.tenantId(), project.id(), 2, 5,
                "现代无尘车间微距，冷萃萃取机缓缓滴下琥珀色澄澈茶汤，回旋入极简设计玻璃瓶。收尾画面：瓶身冷雾凝结，金光流转。",
                "0添加，0蔗糖，将东方原叶茶的纯粹，送入现代都市人的每一天。",
                "沉稳男声", "normal",
                "0糖0脂东方原叶茶，纯粹日常",
                List.of("0添加", "东方原叶茶"),
                "温暖醇厚的管弦乐铺垫",
                TransitionType.MATCH_CUT, "茶汤滴落切入都市生活"
        ));
        scenes.add(StudioScene.create(
                project.tenantId(), project.id(), 3, 5,
                "写字楼落地窗前，年轻人抿了一口茶饮，脸上浮现惬意松弛的微笑，窗外是车水马龙的繁华城市。收尾画面：品牌Logo与Slogan优雅浮现。",
                "在快节奏的时代里，敬每一个认真生活的人。探索你的原叶时刻。",
                "沉稳男声", "slow",
                "慢享甘甜，敬认真生活的你",
                List.of("慢享甘甜", "认真生活"),
                "悠扬治愈的定音收尾",
                TransitionType.HARD_CUT, "品牌Logo定格"
        ));
    }

    private void generatePromoScenes(StudioProject project, List<StudioScene> scenes) {
        scenes.add(StudioScene.create(
                project.tenantId(), project.id(), 1, 4,
                "红黄色炫光炸裂大特写，巨大立体的「五折封顶」字体飞出，周围伴随金币礼花爆炸效果。收尾画面：红色闹钟指针疯狂旋转倒计时。",
                "注意！一年仅此一次的狂欢大促，今晚24点正式锁单封库！",
                "高亢男声", "fast",
                "一年仅此一次！今晚24点锁单",
                List.of("仅此一次", "今晚24点"),
                "重金属摇滚BPM130急迫鼓点",
                TransitionType.CHAIN_EXTEND, "倒计时切入爆品抢单"
        ));
        scenes.add(StudioScene.create(
                project.tenantId(), project.id(), 2, 5,
                "快节奏分屏展示，原本四位数的昂贵价格瞬间被绿光刀片砍成两位数，赠品像瀑布一样源源不断堆满屏幕。收尾画面：满屏整齐排列的现货礼盒。",
                "不要999，不要299，今天下单整整拉满8大件，通通包邮直接送到家！",
                "高亢男声", "fast",
                "狂降到底！直拉满8大件包邮",
                List.of("狂降到底", "8大件包邮"),
                "欢快急促的倒计时声浪",
                TransitionType.HARD_CUT, "切入下单动作"
        ));
        scenes.add(StudioScene.create(
                project.tenantId(), project.id(), 3, 4,
                "主播双手用力指引屏幕下方，红色弹窗闪烁「专属大额券生效中」，倒计时仅剩几秒。收尾画面：画面定格在「抢」字徽标与手势箭头。",
                "专属福利仅限视频前500名，手慢无，点击下方马上抢！",
                "高亢男声", "fast",
                "仅限前500名！手慢无立抢",
                List.of("前500名", "手慢无"),
                "急促倒计时终止音",
                TransitionType.HARD_CUT, "最终定格"
        ));
    }

    private void generateLocalLifeScenes(StudioProject project, List<StudioScene> scenes) {
        scenes.add(StudioScene.create(
                project.tenantId(), project.id(), 1, 4,
                "第一视角手持推开木质暖帘，热闹腾腾的炭火烤肉香气扑面而来，店内座无虚席热火朝天。收尾画面：炭火上一块雪花和牛滋滋冒油。",
                "本地土著私藏的这家炭火烤肉，终于被我挖到了，排队两小时都要吃！",
                "亲和女声", "normal",
                "土著私藏烤肉！排队两小时也要吃",
                List.of("私藏烤肉", "排队两小时"),
                "轻松欢快的爵士吉他小调",
                TransitionType.CHAIN_EXTEND, "和牛冒油切入蘸料包裹"
        ));
        scenes.add(StudioScene.create(
                project.tenantId(), project.id(), 2, 5,
                "超高清微距慢动作，金黄焦脆的厚切牛舌在特调秘制酱汁中滚了一圈，包裹上翠绿生菜。收尾画面：主播大口咬下，满脸幸福满足。",
                "入口直接爆汁，肉质嫩到不可思议，这口炭火香简直直击灵魂！",
                "亲和女声", "normal",
                "入口直接爆汁！嫩到不可思议",
                List.of("爆汁", "不可思议"),
                "欢快满足的生活节奏音效",
                TransitionType.MATCH_CUT, "大口吃肉切入账单优惠"
        ));
        scenes.add(StudioScene.create(
                project.tenantId(), project.id(), 3, 4,
                "主播手拿结账小票展示，上方清晰打印4折套餐，随后手机界面展示一键团购核销券。收尾画面：定格在双人套餐价目与抢购定位图标。",
                "现在左下角双人豪华套餐只要99，不限时段通用，周末赶紧带朋友冲！",
                "亲和女声", "normal",
                "双人豪华餐只要99！周末赶紧冲",
                List.of("只要99", "赶紧冲"),
                "欢快清脆的定音收尾",
                TransitionType.HARD_CUT, "团购定位卡片定格"
        ));
    }
}
