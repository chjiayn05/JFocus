import os
import requests
import json
import time

# 設定路徑
BASE_SAVE_PATH = "./res/pokemon"
JSON_PATH = "./res/pokemon_data.json"
RAW_URL_BASE = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/{id}.png"

# 🌟 【更新】四個參數：(資料夾名稱, [階段ID], 屬性, 稀有度)
# 稀有度請填 "STANDARD" (普通球) 或 "RARE" (大師球)

# 🌟 第一批名單：包含 1~4 世代經典家族 (共 51 家族 / 153 隻)
# 格式: ("ID_英文名", [一階ID, 二階ID, 三階ID], "最終屬性", "稀有度")


POKEMON_FAMILIES = [
        # ================= 第一世代 (Kanto) =================
        ("001_bulbasaur", [1, 2, 3], "草 / 毒", "STANDARD"),       # 妙蛙種子
        ("004_charmander", [4, 5, 6], "火 / 飛行", "STANDARD"),    # 小火龍
        ("007_squirtle", [7, 8, 9], "水", "STANDARD"),             # 傑尼龜
        ("010_caterpie", [10, 11, 12], "蟲 / 飛行", "STANDARD"),   # 綠毛蟲
        ("013_weedle", [13, 14, 15], "蟲 / 毒", "STANDARD"),       # 獨角蟲
        ("016_pidgey", [16, 17, 18], "一般 / 飛行", "STANDARD"),   # 波波
        ("029_nidoran_f", [29, 30, 31], "毒 / 地面", "STANDARD"),  # 尼多蘭
        ("032_nidoran_m", [32, 33, 34], "毒 / 地面", "STANDARD"),  # 尼多朗
        ("041_zubat", [41, 42, 169], "毒 / 飛行", "STANDARD"),     # 超音蝠 (跨世代進化：叉字蝠)
        ("043_oddish", [43, 44, 45], "草 / 毒", "STANDARD"),       # 走路草
        ("060_poliwag", [60, 61, 62], "水 / 格鬥", "STANDARD"),    # 蚊香蝌蚪
        ("063_abra", [63, 64, 65], "超能力", "STANDARD"),          # 凱西
        ("066_machop", [66, 67, 68], "格鬥", "STANDARD"),          # 腕力
        ("069_bellsprout", [69, 70, 71], "草 / 毒", "STANDARD"),   # 喇叭芽
        ("074_geodude", [74, 75, 76], "岩石 / 地面", "STANDARD"),  # 小拳石
        ("081_magnemite", [81, 82, 462], "電 / 鋼", "STANDARD"),   # 小磁怪 (跨世代進化：自爆磁怪)
        ("092_gastly", [92, 93, 94], "幽靈 / 毒", "STANDARD"),     # 鬼斯
        ("116_horsea", [116, 117, 230], "水 / 龍", "STANDARD"),    # 墨海馬 (跨世代進化：刺龍王)
        ("137_porygon", [137, 233, 474], "一般", "STANDARD"),      # 多邊獸 (跨世代進化：多邊獸Z)
        ("147_dratini", [147, 148, 149], "龍 / 飛行", "RARE"),     # 迷你龍 💎 [大師池] 快龍家族

        # ================= 第二世代 (Johto) =================
        ("152_chikorita", [152, 153, 154], "草", "STANDARD"),      # 菊草葉
        ("155_cyndaquil", [155, 156, 157], "火", "STANDARD"),      # 火球鼠
        ("158_totodile", [158, 159, 160], "水", "STANDARD"),       # 小鋸鱷
        ("172_pichu", [172, 25, 26], "電", "STANDARD"),            # 皮丘 -> 皮卡丘 -> 雷丘
        ("173_cleffa", [173, 35, 36], "妖精", "STANDARD"),         # 皮寶寶 -> 皮皮 -> 皮可西
        ("174_igglybuff", [174, 39, 40], "一般 / 妖精", "STANDARD"), # 寶寶丁 -> 胖丁 -> 胖可丁
        ("175_togepi", [175, 176, 468], "妖精 / 飛行", "RARE"),    # 波克比 💎 [大師池] 波克基斯家族
        ("179_mareep", [179, 180, 181], "電", "STANDARD"),         # 咩利羊
        ("187_hoppip", [187, 188, 189], "草 / 飛行", "STANDARD"),  # 毽子草
        ("239_elekid", [239, 125, 466], "電", "STANDARD"),         # 電擊怪 -> 電擊獸 -> 電擊魔獸
        ("240_magby", [240, 126, 467], "火", "STANDARD"),          # 鴨嘴寶寶 -> 鴨嘴火獸 -> 鴨嘴炎獸
        ("246_larvitar", [246, 247, 248], "岩石 / 惡", "RARE"),    # 幼基拉斯 💎 [大師池] 班基拉斯家族

        # ================= 第三世代 (Hoenn) =================
        ("252_treecko", [252, 253, 254], "草", "STANDARD"),        # 木守宮
        ("255_torchic", [255, 256, 257], "火 / 格鬥", "STANDARD"), # 火稚雞
        ("258_mudkip", [258, 259, 260], "水 / 地面", "STANDARD"),  # 水躍魚
        ("270_lotad", [270, 271, 272], "水 / 草", "STANDARD"),     # 蓮葉童子
        ("273_seedot", [273, 274, 275], "草 / 惡", "STANDARD"),    # 橡實果
        ("280_ralts", [280, 281, 282], "超能力 / 妖精", "STANDARD"), # 拉魯拉絲
        ("287_slakoth", [287, 288, 289], "一般", "RARE"),          # 懶人獺 💎 [大師池] 請假王家族
        ("293_whismur", [293, 294, 295], "一般", "STANDARD"),      # 咕妞妞
        ("304_aron", [304, 305, 306], "鋼 / 岩石", "STANDARD"),    # 可可多拉
        ("328_trapinch", [328, 329, 330], "地面 / 龍", "STANDARD"), # 大顎蟻
        ("363_spheal", [363, 364, 365], "冰 / 水", "STANDARD"),    # 海豹球
        ("371_bagon", [371, 372, 373], "龍 / 飛行", "RARE"),       # 寶貝龍 💎 [大師池] 暴飛龍家族
        ("374_beldum", [374, 375, 376], "鋼 / 超能力", "RARE"),    # 鐵啞鈴 💎 [大師池] 巨金怪家族

        # ================= 第四世代 (Sinnoh) =================
        ("387_turtwig", [387, 388, 389], "草 / 地面", "STANDARD"), # 草苗龜
        ("390_chimchar", [390, 391, 392], "火 / 格鬥", "STANDARD"),# 小火焰猴
        ("393_piplup", [393, 394, 395], "水 / 鋼", "STANDARD"),    # 波加曼
        ("396_starly", [396, 397, 398], "一般 / 飛行", "STANDARD"),# 姆克兒
        ("403_shinx", [403, 404, 405], "電", "STANDARD"),          # 小貓怪
        ("443_gible", [443, 444, 445], "龍 / 地面", "RARE"),       # 圓陸鯊 💎 [大師池] 烈咬陸鯊家族
        
        # ================= 跨世代補充包 =================
        ("111_rhyhorn", [111, 112, 464], "地面 / 岩石", "STANDARD"), # 獨角犀牛 (跨世代：超甲狂犀)
        ("220_swinub", [220, 221, 473], "冰 / 地面", "STANDARD"),  # 小山豬 (跨世代：象牙豬)
        ("355_duskull", [355, 356, 477], "幽靈", "STANDARD"),      # 夜巡靈 (跨世代：黑夜魔靈)
        ("406_budew", [406, 315, 407], "草 / 毒", "STANDARD"),     # 含羞苞 (跨世代：羅絲雷朵)
        ("440_happiny", [440, 113, 242], "一般", "STANDARD"),      # 小福蛋 (跨世代：幸福蛋)

        # ================= 第五世代 (Unova) =================
        ("495_snivy", [495, 496, 497], "草", "STANDARD"),          # 藤藤蛇
        ("498_tepig", [498, 499, 500], "火 / 格鬥", "STANDARD"),   # 暖暖豬
        ("501_oshawott", [501, 502, 503], "水", "STANDARD"),       # 水水獺
        ("506_lillipup", [506, 507, 508], "一般", "STANDARD"),     # 小約克
        ("519_pidove", [519, 520, 521], "一般 / 飛行", "STANDARD"),# 豆豆鴿
        ("524_roggenrola", [524, 525, 526], "岩石", "STANDARD"),   # 石丸子
        ("532_timburr", [532, 533, 534], "格鬥", "STANDARD"),      # 搬運小匠
        ("535_tympole", [535, 536, 537], "水 / 地面", "STANDARD"), # 圓蝌蚪
        ("540_sewaddle", [540, 541, 542], "蟲 / 草", "STANDARD"),  # 蟲寶包
        ("543_venipede", [543, 544, 545], "蟲 / 毒", "STANDARD"),  # 百足蜈蚣
        ("551_sandile", [551, 552, 553], "地面 / 惡", "STANDARD"), # 黑眼鱷
        ("574_gothita", [574, 575, 576], "超能力", "STANDARD"),    # 哥德寶寶
        ("577_solosis", [577, 578, 579], "超能力", "STANDARD"),    # 單卵細胞球
        ("582_vanillite", [582, 583, 584], "冰", "STANDARD"),      # 迷你冰
        ("599_klink", [599, 600, 601], "鋼", "STANDARD"),          # 齒輪兒
        ("602_tynamo", [602, 603, 604], "電", "STANDARD"),         # 麻麻小魚
        ("607_litwick", [607, 608, 609], "幽靈 / 火", "STANDARD"), # 燭光靈
        ("610_axew", [610, 611, 612], "龍", "STANDARD"),           # 牙牙
        ("633_deino", [633, 634, 635], "惡 / 龍", "RARE"),         # 單首龍 💎 [大師池] 三首惡龍

        # ================= 第六世代 (Kalos) =================
        ("650_chespin", [650, 651, 652], "草 / 格鬥", "STANDARD"), # 哈力栗
        ("653_fennekin", [653, 654, 655], "火 / 超能力", "STANDARD"),# 火狐狸
        ("656_froakie", [656, 657, 658], "水 / 惡", "STANDARD"),   # 呱呱泡蛙
        ("661_fletchling", [661, 662, 663], "火 / 飛行", "STANDARD"),# 小箭雀
        ("664_scatterbug", [664, 665, 666], "蟲 / 飛行", "STANDARD"),# 粉蝶蟲
        ("669_flabebe", [669, 670, 671], "妖精", "STANDARD"),      # 花蓓蓓
        ("679_honedge", [679, 680, 681], "鋼 / 幽靈", "STANDARD"), # 獨劍鞘
        ("704_goomy", [704, 705, 706], "龍", "RARE"),              # 黏黏寶 💎 [大師池] 黏美龍

        # ================= 第七世代 (Alola) =================
        ("722_rowlet", [722, 723, 724], "草 / 幽靈", "STANDARD"),  # 木木梟
        ("725_litten", [725, 726, 727], "火 / 惡", "STANDARD"),    # 火斑喵
        ("728_popplio", [728, 729, 730], "水 / 妖精", "STANDARD"), # 球球海獅
        ("731_pikipek", [731, 732, 733], "一般 / 飛行", "STANDARD"),# 小篤兒
        ("736_grubbin", [736, 737, 738], "蟲 / 電", "STANDARD"),   # 強顎雞母蟲
        ("761_bounsweet", [761, 762, 763], "草", "STANDARD"),      # 甜竹竹
        ("782_jangmo_o", [782, 783, 784], "龍 / 格鬥", "RARE"),    # 心鱗寶 💎 [大師池] 杖尾鱗甲龍

        # ================= 第八世代 (Galar) =================
        ("810_grookey", [810, 811, 812], "草", "STANDARD"),        # 敲音猴
        ("813_scorbunny", [813, 814, 815], "火", "STANDARD"),      # 炎兔兒
        ("816_sobble", [816, 817, 818], "水", "STANDARD"),         # 淚眼蜥
        ("821_rookidee", [821, 822, 823], "飛行 / 鋼", "STANDARD"),# 稚山雀
        ("824_blipbug", [824, 825, 826], "蟲 / 超能力", "STANDARD"),# 索偵蟲
        ("837_rolycoly", [837, 838, 839], "岩石 / 火", "STANDARD"),# 小炭仔
        ("856_hatenna", [856, 857, 858], "超能力 / 妖精", "STANDARD"),# 迷布莉姆
        ("859_impidimp", [859, 860, 861], "惡 / 妖精", "STANDARD"),# 搗蛋小妖
        ("885_dreepy", [885, 886, 887], "龍 / 幽靈", "RARE"),      # 多龍梅西亞 💎 [大師池] 多龍巴魯托

        # ================= 第九世代 (Paldea) =================
        ("906_sprigatito", [906, 907, 908], "草 / 惡", "STANDARD"),# 新葉喵
        ("909_fuecoco", [909, 910, 911], "火 / 幽靈", "STANDARD"), # 呆火鱷
        ("912_quaxly", [912, 913, 914], "水 / 格鬥", "STANDARD"),  # 潤水鴨
        ("921_pawmi", [921, 922, 923], "電 / 格鬥", "STANDARD"),   # 布撥
        ("928_smoliv", [928, 929, 930], "草 / 一般", "STANDARD"),  # 奧利紐
        ("932_nacli", [932, 933, 934], "岩石", "STANDARD"),        # 鹽石寶
        ("957_tinkatink", [957, 958, 959], "妖精 / 鋼", "STANDARD"),# 小鍛匠
        ("996_frigibax", [996, 997, 998], "龍 / 冰", "RARE"),      # 涼脊龍 💎 [大師池] 戟脊龍 (幫你補上了)
    
    
    # ================= 分支進化與跨世代家族 =================
    ("298_azurill", [298, 183, 184], "水 / 妖精", "STANDARD"),     # 露力麗 -> 瑪力露 -> 瑪力露麗
    ("265_beautifly", [265, 266, 267], "蟲 / 飛行", "STANDARD"),   # 刺尾蟲 -> 狩獵鳳蝶
    ("265_dustox", [265, 268, 269], "蟲 / 毒", "STANDARD"),        # 刺尾蟲 -> 毒粉蛾
    ("043_bellossom", [43, 44, 182], "草", "STANDARD"),            # 走路草 -> 美麗花
    ("060_politoed", [60, 61, 186], "水", "STANDARD"),             # 蚊香蝌蚪 -> 蚊香蛙皇
    ("280_gallade", [280, 281, 475], "超能力 / 格鬥", "STANDARD"),   # 拉魯拉絲 -> 艾路雷朵
    ("439_mime_jr", [439, 122, 866], "超能力 / 冰", "STANDARD"),    # 魔尼尼 -> 踏冰人偶
    ("840_applin", [840, 1011, 1019], "草 / 龍", "STANDARD"),      # 啃果蟲 -> 蜜集大蛇

    # ================= 究極大師獎池 (神獸降臨) =================
    # 💡 利用重複 ID 的技巧，讓無進化的神獸也能完美適應三階段系統！
    ("789_solgaleo", [789, 790, 791], "超能力 / 鋼", "RARE"),      # 科斯莫古 -> 索爾迦雷歐
    ("789_lunala", [789, 790, 792], "超能力 / 幽靈", "RARE"),      # 科斯莫古 -> 露奈雅拉
    ("150_mewtwo", [150, 150, 150], "超能力", "RARE"),             # 💎 超夢
    ("151_mew", [151, 151, 151], "超能力", "RARE"),                # 💎 夢幻
    ("249_lugia", [249, 249, 249], "超能力 / 飛行", "RARE"),       # 💎 洛奇亞
    ("250_ho_oh", [250, 250, 250], "火 / 飛行", "RARE"),           # 💎 鳳王
    ("251_celebi", [251, 251, 251], "超能力 / 草", "RARE"),        # 💎 時拉比
    ("382_kyogre", [382, 382, 382], "水", "RARE"),                 # 💎 蓋歐卡
    ("383_groudon", [383, 383, 383], "地面", "RARE"),              # 💎 固拉多
    ("384_rayquaza", [384, 384, 384], "龍 / 飛行", "RARE"),        # 💎 烈空坐
    ("385_jirachi", [385, 385, 385], "鋼 / 超能力", "RARE"),       # 💎 基拉祈
    ("483_dialga", [483, 483, 483], "鋼 / 龍", "RARE"),            # 💎 帝牙盧卡
    ("484_palkia", [484, 484, 484], "水 / 龍", "RARE"),            # 💎 帕路奇亞
    ("487_giratina", [487, 487, 487], "幽靈 / 龍", "RARE"),        # 💎 騎拉帝納
    ("493_arceus", [493, 493, 493], "一般", "RARE"),               # 💎 阿爾宙斯
    ("888_zacian", [888, 888, 888], "妖精 / 鋼", "RARE"),          # 💎 蒼響
    
    # ================= 究極大師獎池擴充包 (神獸與幻之寶可夢) =================
    # --- 第一世代 ---
    ("144_articuno", [144, 144, 144], "冰 / 飛行", "RARE"),        # 💎 急凍鳥
    ("145_zapdos", [145, 145, 145], "電 / 飛行", "RARE"),          # 💎 閃電鳥
    ("146_moltres", [146, 146, 146], "火 / 飛行", "RARE"),         # 💎 火焰鳥

    # --- 第二世代 ---
    ("243_raikou", [243, 243, 243], "電", "RARE"),                 # 💎 雷公
    ("244_entei", [244, 244, 244], "火", "RARE"),                  # 💎 炎帝
    ("245_suicune", [245, 245, 245], "水", "RARE"),                # 💎 水君

    # --- 第三世代 ---
    ("380_latias", [380, 380, 380], "龍 / 超能力", "RARE"),        # 💎 拉帝亞斯
    ("381_latios", [381, 381, 381], "龍 / 超能力", "RARE"),        # 💎 拉帝歐斯
    ("386_deoxys", [386, 386, 386], "超能力", "RARE"),             # 💎 代歐奇希斯

    # --- 第四世代 ---
    ("488_cresselia", [488, 488, 488], "超能力", "RARE"),          # 💎 克雷色利亞
    ("491_darkrai", [491, 491, 491], "惡", "RARE"),                # 💎 達克萊伊
    ("492_shaymin", [492, 492, 492], "草", "RARE"),                # 💎 謝米

    # --- 第五世代 ---
    ("494_victini", [494, 494, 494], "超能力 / 火", "RARE"),       # 💎 比克提尼
    ("638_cobalion", [638, 638, 638], "鋼 / 格鬥", "RARE"),        # 💎 勾帕路翁
    ("639_terrakion", [639, 639, 639], "岩石 / 格鬥", "RARE"),     # 💎 代拉基翁
    ("640_virizion", [640, 640, 640], "草 / 格鬥", "RARE"),        # 💎 畢力吉翁
    ("643_reshiram", [643, 643, 643], "龍 / 火", "RARE"),          # 💎 萊希拉姆
    ("644_zekrom", [644, 644, 644], "龍 / 電", "RARE"),            # 💎 捷克羅姆
    ("646_kyurem", [646, 646, 646], "龍 / 冰", "RARE"),            # 💎 酋雷姆

    # --- 第六世代 ---
    ("716_xerneas", [716, 716, 716], "妖精", "RARE"),              # 💎 哲爾尼亞斯
    ("717_yveltal", [717, 717, 717], "惡 / 飛行", "RARE"),         # 💎 伊裴爾塔爾
    ("718_zygarde", [718, 718, 718], "龍 / 地面", "RARE"),         # 💎 基格爾德
    ("719_diancie", [719, 719, 719], "岩石 / 妖精", "RARE"),       # 💎 蒂安希

    # --- 第七世代 ---
    ("785_tapu_koko", [785, 785, 785], "電 / 妖精", "RARE"),       # 💎 卡璞・鳴鳴
    ("800_necrozma", [800, 800, 800], "超能力", "RARE"),           # 💎 奈克洛茲瑪
    ("807_zeraora", [807, 807, 807], "電", "RARE"),                # 💎 捷拉奧拉

    # --- 第八世代 ---
    ("889_zamazenta", [889, 889, 889], "格鬥 / 鋼", "RARE"),       # 💎 藏瑪然特
    ("890_eternatus", [890, 890, 890], "毒 / 龍", "RARE"),         # 💎 無極汰那

    # --- 第九世代 ---
    ("1007_koraidon", [1007, 1007, 1007], "格鬥 / 龍", "RARE"),    # 💎 故勒頓
    ("1008_miraidon", [1008, 1008, 1008], "電 / 龍", "RARE"),      # 💎 密勒頓
]


def fetch_metadata():
    pokedex_data = []
    
    # 🌟 【關鍵修改】：檢查是不是已經有舊的 JSON，有的話就先讀取進來！
    if os.path.exists(JSON_PATH):
        try:
            with open(JSON_PATH, 'r', encoding='utf-8') as f:
                old_data = json.load(f)
                pokedex_data = old_data.get("pokedex", [])
                print(f"📦 已讀取現有圖鑑：目前共有 {len(pokedex_data)} 個家族，準備接續擴充...")
        except Exception as e:
            print(f"⚠️ 無法讀取舊 JSON，將建立新檔案: {e}")

    if not os.path.exists(BASE_SAVE_PATH):
        os.makedirs(BASE_SAVE_PATH)

    for folder_name, ids, types, rarity in POKEMON_FAMILIES:
        # 🛡️ 【防呆機制】：如果這隻寶可夢已經在圖鑑裡了，就直接跳過，不浪費時間重複抓！
        if any(p["folderName"] == folder_name for p in pokedex_data):
            print(f"⏭️ {folder_name} 已經存在，自動跳過...")
            continue
            
        family_entry = {
            "id": folder_name, 
            "folderName": folder_name,
# ... (下面抓圖片跟 API 的程式碼都不用動，維持原樣) ...
            "name": folder_name.split('_')[1].capitalize(),
            "types": types,
            "rarity": rarity, # 👈 【關鍵新增】將稀有度寫入 JSON
            "stages": []
        }
        
        folder_path = os.path.join(BASE_SAVE_PATH, folder_name)
        os.makedirs(folder_path, exist_ok=True)

        for i, pkmn_id in enumerate(ids, start=1):
            # 1. 下載圖片
            img_url = RAW_URL_BASE.format(id=pkmn_id)
            img_data = requests.get(img_url).content
            with open(os.path.join(folder_path, f"stage{i}.png"), 'wb') as f:
                f.write(img_data)

            # 2. 抓取描述與名稱 (PokeAPI)
            try:
                real_id = int(pkmn_id) 
                species_url = f"https://pokeapi.co/api/v2/pokemon-species/{real_id}/"
                
                headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
                response = requests.get(species_url, headers=headers, timeout=10)
                response.raise_for_status() 
                res = response.json()
                
                names_dict = { n['language']['name'].lower(): n['name'] for n in res.get('names', []) }
                zh_name = names_dict.get('zh-hant', names_dict.get('zh-hans', '未知精靈'))
                
                if "stageNames" not in family_entry:
                    family_entry["stageNames"] = []
                family_entry["stageNames"].append(zh_name)

                if i == 1:
                    family_entry["name"] = zh_name 
                    print(f" ✅ 成功抓取 [{rarity}] 家族: {zh_name}")
                else:
                    print(f"  ✨ 階段 {i} 專屬名稱: {zh_name}") 

                desc_dict = {}
                for desc_info in res.get('flavor_text_entries', []):
                    lang = desc_info['language']['name'].lower() 
                    if 'zh' in lang:
                        desc_dict[lang] = desc_info['flavor_text'].replace('\n', '').replace('\f', '')
                        
                zh_desc = desc_dict.get('zh-hant', desc_dict.get('zh-hans', "這是一隻神秘的寶可夢。"))
                family_entry["stages"].append(zh_desc)

                time.sleep(0.5) 

            except Exception as e:
                print(f"  ⚠️ API 讀取失敗: {e}")
                if "stageNames" not in family_entry: family_entry["stageNames"] = []
                family_entry["stageNames"].append("未知精靈")
                family_entry["stages"].append("資料讀取失敗。")

        pokedex_data.append(family_entry)

    with open(JSON_PATH, 'w', encoding='utf-8') as f:
        json.dump({"pokedex": pokedex_data}, f, ensure_ascii=False, indent=4)

if __name__ == "__main__":
    print("🚀 開始抓取寶可夢資料，請稍候...")
    fetch_metadata()
    print("✅ 圖片與 JSON 數據已全部就緒！去看看你的大師球獎池吧！")