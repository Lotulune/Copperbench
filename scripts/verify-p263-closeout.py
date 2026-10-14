"""Recheck the dated P263 evidence pairs without changing candidate evaluator rules.

The evidence root is the preserved p263-remaining directory. The delivery root
contains the frozen candidate directories. Output is a new review index, not a
replacement for any original run or an authorization to publish.
"""
import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPOSITORY = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPOSITORY / "tools/candidate-evidence"))
import candidate_evidence as ce

CLIENTS = {
    32: ["windows-fabric-g1-client-cell"],
    33: ["windows-neoforge-g1-client-cell"],
    34: ["xorg-fabric-g1-client-cell"],
    35: ["xorg-neoforge-g1-client-cell"],
    36: ["wayland-fabric-g1-client-cell-v2"],
    37: ["wayland-neoforge-g1-client-cell"],
    38: ["windows-fabric-g2-client-cell-v2"],
    39: ["windows-neoforge-g2-client-cell-v2"],
    40: ["xorg-fabric-g2-client-cell", "wayland-fabric-g2-client-cell"],
    41: ["xorg-neoforge-g2-client-cell", "wayland-neoforge-g2-client-cell"],
}
OFFLINE = {
    38: "windows-g2-fabric-offline-review-final",
    39: "windows-g2-neoforge-offline-review-final",
    40: "fabric-g2-order-fixed-offline-review",
    41: "neoforge-g2-order-fixed-offline-review",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence-root", type=Path, required=True)
    parser.add_argument("--delivery-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    base, delivery, output = args.evidence_root, args.delivery_root, args.output
    if output.exists():
        raise ValueError("Use a new output directory; preserve earlier reviews")
    candidates = {}
    for path in sorted((delivery / "candidates").rglob("candidate.json")):
        metadata = ce.read(path)
        candidate_id = metadata["candidateId"]
        if candidate_id in candidates:
            raise ValueError("Ambiguous candidate directory")
        candidates[candidate_id] = path.parent
    installed = {}
    for path in sorted(base.rglob("cell.json")):
        receipt = ce.read(path)
        if receipt.get("cell", "").startswith("install:"):
            installed.setdefault(ce.sha(path), path)
    output.mkdir(parents=True)
    pairs = []
    selected = {}
    for ticket, names in CLIENTS.items():
        for name in names:
            client_path = base / name / "cell.json"
            client = ce.read(client_path)
            install_path = installed[client["installationReceiptSha256"]]
            install = ce.read(install_path)
            candidate_path = candidates[client["candidateId"]]
            evaluation = ce.evaluate(candidate_path, [install_path, client_path])
            cells = {row["cell"]: row for row in evaluation["cells"]}
            assert cells[client["cell"]]["status"] == "passed"
            assert cells[install["cell"]]["status"] == "passed"
            assert not ce.read(candidate_path / "candidate.json")["source"]["dirty"]
            original = base / name / "evaluation.json"
            previous = ce.read(original)
            assert next(row for row in previous["cells"] if row["cell"] == client["cell"])["status"] == "passed"
            evaluation_name = name + "-evaluation.json"
            ce.write(output / evaluation_name, evaluation)
            pair = dict(ticket=ticket, cell=client["cell"], level=client["level"],
                        candidateId=client["candidateId"],
                        candidateDirectory=candidate_path.relative_to(delivery).as_posix(),
                        source=ce.read(candidate_path / "candidate.json")["source"],
                        clientReceipt=(client_path.relative_to(base)).as_posix(),
                        clientReceiptSha256=ce.sha(client_path),
                        installationReceipt=install_path.relative_to(base).as_posix(),
                        installationReceiptSha256=ce.sha(install_path),
                        artifactSha256=client["artifactSha256"],
                        observedArtifacts=client["observedArtifacts"],
                        pairBindingStatus="passed", evaluation=evaluation_name,
                        evaluationSha256=ce.sha(output / evaluation_name),
                        originalEvaluationSha256=ce.sha(original),
                        rawEvaluatorOverallStatus=evaluation["status"],
                        rawEvaluatorScopes=evaluation["scopes"],
                        limitations=client.get("limitations", []))
            pairs.append(pair)
            selected[name] = (candidate_path, install_path, client_path)
            print(json.dumps(dict(ticket=ticket, cell=pair["cell"], status="passed")), flush=True)
    # The existing evaluator can select both Windows loader installations.
    windows_receipts = []
    for ticket in (38, 39):
        candidate_path, install_path, client_path = selected[CLIENTS[ticket][0]]
        windows_receipts.extend([install_path, client_path])
    windows = ce.evaluate(candidate_path, windows_receipts)
    assert len(windows["scopes"]) == 2 and all(row["qualificationEligible"] for row in windows["scopes"])
    ce.write(output / "windows-g2-evaluation.json", windows)
    reviews = []
    for ticket, directory in OFFLINE.items():
        path = base / directory / "results.json"
        results = ce.read(path)
        valid = [r for r in results if r["kind"] != "tampered"]
        tampered = [r for r in results if r["kind"] == "tampered"]
        assert len(valid) == 4 and len(tampered) == 2
        assert all(r["exitCode"] == 0 and r["result"]["status"] == "integrity_verified" for r in valid)
        assert all(r["exitCode"] != 0 and r["result"]["status"] == "failed" for r in tampered)
        reviews.append(dict(ticket=ticket, results=path.relative_to(base).as_posix(),
                            sha256=ce.sha(path), validPassed=4, tamperedRejected=2))
    regression = []
    for path in sorted((base / "cancellation-and-legacy-test-results").glob("*.xml")):
        suite = ET.parse(path).getroot()
        counts = {k: int(suite.get(k, "0")) for k in ("tests", "failures", "errors", "skipped")}
        assert counts["tests"] > 0 and not any(counts[k] for k in ("failures", "errors", "skipped"))
        regression.append(dict(path=path.relative_to(base).as_posix(), sha256=ce.sha(path), **counts))
    assert sum(row["tests"] for row in regression) == 44
    scopes = []
    for ticket in (38, 39, 40, 41):
        rows = [p for p in pairs if p["ticket"] == ticket]
        for key in ("candidateId", "artifactSha256", "observedArtifacts", "source"):
            assert all(p[key] == rows[0][key] for p in rows)
        if ticket >= 40:
            assert {p["cell"].split(":")[1] for p in rows} == {"xorg", "wayland"}
            assert len({p["installationReceiptSha256"] for p in rows}) == 2
        scopes.append(dict(ticket=ticket, scope=rows[0]["cell"].split(":")[-1] + ":G2:" + ("windows" if ticket < 40 else "ubuntu"),
                           candidateId=rows[0]["candidateId"], artifactSha256=rows[0]["artifactSha256"],
                           reviewedQualification="passed_with_documented_limits", cells=[p["cell"] for p in rows],
                           basis="strict_pair_checks_and_reviewed_same_candidate_desktop_coverage" if ticket >= 40 else "existing_evaluator_complete_scope_and_reviewed_player_evidence",
                           formalSupportClaim=False, published=False))
    ce.write(output / "qualification.json", dict(
        schemaVersion="1", date="2026-10-14", kind="dated_p263_review_index",
        evidenceRoot="evidence/p263-remaining", pairs=pairs, g2Scopes=scopes,
        offlineReviews=reviews, reusedRegression=regression,
        regressionSource="2026-10-13 execution; not rerun during documentation closeout",
        formalSupportClaim=False, producerAuthenticated=False, published=False,
        method="Original evaluator unchanged. Each selected client is bound to its actual installation. Ubuntu coverage is a separate review across two strict pairs, not a synthetic installation receipt or automatic whole-matrix pass.",
        unavailableAndUnselectedCells="Preserved as not_run in each original and reproduced evaluation."))


if __name__ == "__main__":
    main()
