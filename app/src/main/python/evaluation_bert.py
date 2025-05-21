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
    cand = """Macam macam tawaf yang ada dalam konteks tersebut adalah: 
- Tawaf rukun, 
- Tawaf qudum, 
- Tawaf sunat, 
- Tawaf wada’ dan tawaf nadzar. 
Selain itu, ada tiga macam pelaksanaan haji, yaitu: 
- Haji ifrād, 
- Haji qirān, dan 
- Haji tamattu."""

    result = evaluate_with_bertscore(ref, cand)
    print(result)
