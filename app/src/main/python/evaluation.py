def evaluate(reference, candidate):
    from nltk.translate.bleu_score import sentence_bleu, SmoothingFunction
    from rouge_score import rouge_scorer

    ref_tokens = reference.split()
    cand_tokens = candidate.split()

    # Gunakan SmoothingFunction untuk mencegah skor BLEU menjadi 0
    smoothing_function = SmoothingFunction().method1
    bleu = sentence_bleu([ref_tokens], cand_tokens, smoothing_function=smoothing_function)

    scorer = rouge_scorer.RougeScorer(['rougeL'], use_stemmer=True)
    rouge = scorer.score(reference, candidate)
    rouge_l = rouge['rougeL'].fmeasure

    print(f"[PYTHON] BLEU: {bleu}, ROUGE-L: {rouge_l}")

    return dict(bleu=float(bleu), rouge_l=float(rouge_l))

# Tambahan ini supaya bisa dipanggil langsung dari command line atau sebagai test
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
    result = evaluate(ref, cand)
    print(result)