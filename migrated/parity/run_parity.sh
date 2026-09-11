#!/usr/bin/env bash
# Behavioural parity check: unmodified legacy CBTRN02C (compiled with GnuCOBOL)
# vs. the migrated Java PostTransactionsJob, run on
#   1. the shipped sample data  app/data/ASCII
#   2. a generated edge-case set parity/edge/gen_edge_data.sh
#
# Requires: cobc (GnuCOBOL 3.x), java 17, mvn (only if target/carddemo.jar is missing).
# Usage: migrated/parity/run_parity.sh [work-dir]
set -euo pipefail

MIG="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="$(cd "$MIG/.." && pwd)"
WORK="${1:-$MIG/target/parity}"
JAR="$MIG/target/carddemo.jar"

rm -rf "$WORK"
mkdir -p "$WORK/bin"

echo "== building legacy CBTRN02C with GnuCOBOL"
cobc -x -std=ibm -fsign=EBCDIC -I "$ROOT/app/cpy" \
     -o "$WORK/bin/CBTRN02C" "$ROOT/app/cbl/CBTRN02C.cbl" "$MIG/parity/CEE3ABD.cbl"
cobc -x -std=ibm -fsign=EBCDIC -o "$WORK/bin/PARITYIO" "$MIG/parity/PARITYIO.cbl"

if [ ! -f "$JAR" ]; then
  echo "== building carddemo.jar"
  (cd "$MIG" && mvn -q -B package -DskipTests)
fi

# Trailing spaces are not significant (LINE SEQUENTIAL output strips them).
# TRAN-PROC-TS (cols 305-330) is the run timestamp and is masked.
# A negative-zero TRAN-AMT (cols 133-143, '0000000000}') is an accepted deviation
# (DEVIATIONS.md D9): the Java side has no -0, so it is folded to +0 on both sides.
normalize() { sed -e 's/[[:space:]]*$//' "$1"; }
mask_tran() {
  sed -E -e 's/^(.{304}).{26}/\1##########################/' \
         -e 's/^(.{132})0000000000\}/\10000000000{/'
}

overall=0

run_dataset() {
  local name="$1" data="$2"
  local w="$WORK/$name"
  mkdir -p "$w/legacy" "$w/java"
  echo
  echo "==== data set: $name ($data)"

  # --- legacy run -----------------------------------------------------------
  export DD_ACCTFILE="$w/legacy/acctfile.idx"
  export DD_XREFFILE="$w/legacy/xreffile.idx"
  export DD_TCATBALF="$w/legacy/tcatbalf.idx"
  export DD_TRANFILE="$w/legacy/tranfile.idx"
  export DD_DALYTRAN="$w/legacy/dalytran.seq"
  export DD_DALYREJS="$w/legacy/dalyrejs.seq"

  DD_INFILE="$data/acctdata.txt"  "$WORK/bin/PARITYIO" LOAD ACCT
  DD_INFILE="$data/cardxref.txt"  "$WORK/bin/PARITYIO" LOAD XREF
  DD_INFILE="$data/tcatbal.txt"   "$WORK/bin/PARITYIO" LOAD TCAT
  DD_INFILE="$data/dailytran.txt" "$WORK/bin/PARITYIO" TOSEQ

  set +e
  "$WORK/bin/CBTRN02C" > "$w/legacy/stdout.txt"
  local legacy_rc=$?
  set -e

  DD_OUTFILE="$w/legacy/rejects.txt"      "$WORK/bin/PARITYIO" FROMSEQ
  DD_OUTFILE="$w/legacy/account.txt"      "$WORK/bin/PARITYIO" DUMP ACCT
  DD_OUTFILE="$w/legacy/tran_cat_bal.txt" "$WORK/bin/PARITYIO" DUMP TCAT
  DD_OUTFILE="$w/legacy/transaction.txt"  "$WORK/bin/PARITYIO" DUMP TRAN

  # --- migrated run ---------------------------------------------------------
  local db="$w/java/carddemo.db"
  java -jar "$JAR" load --db "$db" --acctdata "$data/acctdata.txt" \
       --cardxref "$data/cardxref.txt" --tcatbal "$data/tcatbal.txt" > /dev/null
  set +e
  java -jar "$JAR" post-transactions --db "$db" --dalytran "$data/dailytran.txt" \
       --rejects "$w/java/rejects.txt" > "$w/java/stdout.txt"
  local java_rc=$?
  set -e
  for t in account tran_cat_bal transaction; do
    java -jar "$JAR" dump --db "$db" --table "$t" --out "$w/java/$t.txt"
  done

  # --- compare --------------------------------------------------------------
  local fail=0
  cmp_files() {  # file, transform
    local f="$1" xform="${2:-cat}"
    if diff <(normalize "$w/legacy/$f" | $xform) <(normalize "$w/java/$f" | $xform) \
          > "$w/$f.diff"; then
      echo "PASS $f ($(wc -l < "$w/java/$f") records)"
    else
      echo "FAIL $f — see $w/$f.diff"; fail=1
    fi
  }
  cmp_files stdout.txt
  cmp_files rejects.txt
  cmp_files account.txt
  cmp_files tran_cat_bal.txt
  cmp_files transaction.txt mask_tran
  if [ "$legacy_rc" -eq "$java_rc" ]; then
    echo "PASS return code ($java_rc)"
  else
    echo "FAIL return code: legacy=$legacy_rc java=$java_rc"; fail=1
  fi
  if [ $fail -ne 0 ]; then overall=1; fi
}

run_dataset sample "$ROOT/app/data/ASCII"
"$MIG/parity/edge/gen_edge_data.sh" "$WORK/edge-data" > /dev/null
run_dataset edge "$WORK/edge-data"

echo
if [ $overall -eq 0 ]; then echo "PARITY OK"; else echo "PARITY FAILED"; exit 1; fi
