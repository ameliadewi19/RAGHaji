from bert_score import score

def evaluate_with_bertscore(reference, candidate):
    # Menghitung BERTScore untuk precision, recall, dan F1
    P, R, F1 = score([candidate], [reference], lang="id")  # Ganti lang="id" jika teks berbahasa Indonesia

    # Menampilkan hasilnya
    print(f"Precision: {P.mean().item()}")
    print(f"Recall: {R.mean().item()}")
    print(f"F1-Score: {F1.mean().item()}")

    # Mengembalikan hasil dalam bentuk dictionary
    return {
        'precision': P.mean().item(),
        'recall': R.mean().item(),
        'f1': F1.mean().item()
    }

# Contoh penggunaan
if __name__ == "__main__":
    ref = """Tawaf ada 5 (lima) macam, yaitu:
    a. Tawaf qudum
    b. Tawaf rukun (ifadah dan umrah)
    c. Tawaf sunat
    d. Tawaf wada’
    e. Tawaf nadzar"""
    cand = """Macam-Macam Tawaf adalah 5, yaitu:
    Tawaf rukun
Tawaf qudum
Tawaf sunat
Tawaf wada’
dan tawaf nadzar.
Sebagai tambahan, macam-macam tawaf di Makkah terdiri dari 5 jenis, yaitu:

- 5 Tawaf rukun, yaitu tawaf rukun haji yang dikerjakan dua kali,
- 5 Tawaf qudum, yaitu tawaf qudum yang dikerjakan di hari pertama kedatangan,
- 5 Tawaf sunat, yaitu tawaf sunat yang dikerjakan di hari kedua kedatangan,
- 5 Tawaf wada’, yaitu tawaf wada’ yang dikerjakan setiap kesempatan masuk ke Masjidil Haram,
- 5 Tawaf nadzar, yaitu tawaf nadzar yang dikerjakan di hari kedua kedatangan di Makkah."""

    result = evaluate_with_bertscore(ref, cand)
    print(result)
