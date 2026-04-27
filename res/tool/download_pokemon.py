import os
import requests
import json

# 設定路徑
BASE_SAVE_PATH = "./res/pokemon"
JSON_PATH = "./res/pokemon_data.json"
RAW_URL_BASE = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/{id}.png"

# 定義你想抓的御三家家族 (ID 名單)
POKEMON_FAMILIES = [
    # --- 第一世代 (Kanto) ---
    ("001_bulbasaur", [1, 2, 3], "草 / 毒"),
    ("004_charmander", [4, 5, 6], "火"),
    ("007_squirtle", [7, 8, 9], "水"),
    ("010_caterpie", [10, 11, 12], "蟲"),
    ("013_weedle", [13, 14, 15], "蟲 / 毒"),
    ("016_pidgey", [16, 17, 18], "一般 / 飛行"),
    ("063_abra", [63, 64, 65], "超能力"),
    ("066_machop", [66, 67, 68], "格鬥"),
    ("092_gastly", [92, 93, 94], "幽靈 / 毒"),
    ("147_dratini", [147, 148, 149], "龍"),

    # --- 第二世代 (Johto) ---
    ("179_mareep", [179, 180, 181], "電"),
    ("246_larvitar", [246, 247, 248], "岩石 / 地面"),

    # --- 第三世代 (Hoenn) ---
    ("280_ralts", [280, 281, 282], "超能力 / 妖精"),
    ("374_beldum", [374, 375, 376], "鋼 / 超能力"),

    # --- 第四世代 (Sinnoh) ---
    ("443_gible", [443, 444, 445], "龍 / 地面"),
]

def fetch_metadata():
    pokedex_data = []
    
    if not os.path.exists(BASE_SAVE_PATH):
        os.makedirs(BASE_SAVE_PATH)

    for folder_name, ids, types in POKEMON_FAMILIES:
        family_entry = {
            # 將 ID 轉為字串並補零，例如 "001"，確保 Java 解析字串不會出錯
            "id": f"{ids[0]:03d}", 
            "folderName": folder_name,
            "name": folder_name.split('_')[1].capitalize(),
            "types": types,
            "stages": []
        }
        
        # 建立資料夾
        folder_path = os.path.join(BASE_SAVE_PATH, folder_name)
        os.makedirs(folder_path, exist_ok=True)

        for i, pkmn_id in enumerate(ids, start=1):
            # 1. 下載圖片
            img_url = RAW_URL_BASE.format(id=pkmn_id)
            img_data = requests.get(img_url).content
            with open(os.path.join(folder_path, f"stage{i}.png"), 'wb') as f:
                f.write(img_data)

            # 2. 抓取描述 (PokeAPI)
            try:
                species_url = f"https://pokeapi.co/api/v2/pokemon-species/{pkmn_id}/"
                res = requests.get(species_url).json()
                # 尋找繁體中文描述
                desc = next((entry['flavor_text'] for entry in res['flavor_text_entries'] 
                            if entry['language']['name'] == 'zh-Hant'), "這是一隻神秘的寶可夢。")
                family_entry["stages"].append(desc.replace('\n', ' '))
            except Exception as e:
                print(f"⚠️ 讀取 {pkmn_id} 描述失敗: {e}")
                family_entry["stages"].append("資料讀取失敗。")
            
            print(f"已完成: {folder_name} Stage {i}")

        pokedex_data.append(family_entry)

    # 儲存為 JSON (修正了這裡的參數錯字)
    with open(JSON_PATH, 'w', encoding='utf-8') as f:
        json.dump({"pokedex": pokedex_data}, f, ensure_ascii=False, indent=4)

if __name__ == "__main__":
    print("🚀 開始抓取寶可夢資料，請稍候...")
    fetch_metadata()
    print("✅ 圖片與 JSON 數據已全部就緒！")