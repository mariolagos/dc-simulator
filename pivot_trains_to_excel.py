import os
import pandas as pd

# Justera denna path till din pivot-katalog
PIVOT_DIR = r"project/3subs2train2/scenario1/pivot"
csv_path = os.path.join(PIVOT_DIR, "trains.csv")
out_xlsx = os.path.join(PIVOT_DIR, "trains_pivot.xlsx")

df = pd.read_csv(csv_path)

# Lång -> bred: index = time_s, kolumnhierarki = (signal, train_id)
wide = (
    df
    .set_index(["time_s", "train_id"])
    .unstack("train_id")       # kolumner: req_W, P_W, ... × Train1/Train2
)

# Platta till MultiIndex-kolumner till "Train1.req_W" etc
wide.columns = [
    f"{train_id}.{signal}"
    for signal, train_id in wide.columns
]

# Tid som vanlig kolumn först
wide.reset_index(inplace=True)

# Fyll NaN med 0 om du vill ha 0 i stället för tomt
wide = wide.fillna(0.0)

# Skriv till Excel-fil med ett blad "trains"
with pd.ExcelWriter(out_xlsx, engine="xlsxwriter") as writer:
    wide.to_excel(writer, sheet_name="trains", index=False)

print(f"Wrote pivoted trains sheet to {out_xlsx}")
