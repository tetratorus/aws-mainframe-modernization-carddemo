#!/usr/bin/env bash
# Generates a small hand-crafted data set that exercises the CBTRN02C paths the
# shipped sample data (app/data/ASCII) does not reach: reject reasons 100/101/103,
# 102+103 on the same record, negative amounts (cycle-debit path), category-balance
# creation before any successful read, FILLER inheritance on created records, and
# high-order truncation of PIC S9(09)V99 / S9(10)V99 arithmetic.
#
# Usage: gen_edge_data.sh OUT_DIR   (writes acctdata.txt cardxref.txt tcatbal.txt dailytran.txt)
set -euo pipefail
OUT="$1"
mkdir -p "$OUT"

# zoned decimal with EBCDIC-style overpunch: zd <integer value in minor units> <digits>
zd() {
  local v="$1" d="$2" neg=0
  if [ "${v:0:1}" = "-" ]; then neg=1; v="${v:1}"; fi
  v=$(printf "%0${d}d" "$v")
  local last="${v: -1}" pos='{ABCDEFGHI' negs='}JKLMNOPQR' ch
  if [ "$neg" = 1 ]; then ch="${negs:$last:1}"; else ch="${pos:$last:1}"; fi
  printf '%s%s' "${v:0:$((d - 1))}" "$ch"
}
x() { printf "%-${2}s" "$1"; }         # PIC X(n)
n() { printf "%0${2}d" "$1"; }          # PIC 9(n)

# acct <id> <status> <currbal> <limit> <cashlimit> <open> <expiry> <reissue> <cyccredit> <cycdebit> <zip> <group>
acct() {
  n "$1" 11; x "$2" 1; zd "$3" 12; zd "$4" 12; zd "$5" 12
  x "$6" 10; x "$7" 10; x "$8" 10; zd "$9" 12; zd "${10}" 12; x "${11}" 10; x "${12}" 10; x "" 178
  printf '\n'
}
xref() { x "$1" 16; n "$2" 9; n "$3" 11; x "" 14; printf '\n'; }
tcat() { n "$1" 11; x "$2" 2; n "$3" 4; zd "$4" 11; x "$5" 22; printf '\n'; }
# tran <id> <type> <cat> <amount-cents> <card> <orig-ts>
tran() {
  x "$1" 16; x "$2" 2; n "$3" 4; x "POS TERM" 10; x "Edge case $1" 100; zd "$4" 11
  n 123456789 9; x "Merchant $1" 50; x "Testville" 50; x "12345" 10; x "$5" 16; x "$6" 26; x "" 26; x "" 20
  printf '\n'
}

{
  #    id  st  currbal      limit         cashlim   open       expiry     reissue    cyccr        cycdb       zip     group
  acct 1   Y   50000        100000        50000     2020-01-01 2030-12-31 2025-01-01 0            0           10001   A0000000
  acct 2   Y   0            10000         0         2015-01-01 2020-01-01 2018-01-01 0            0           10002   A0000000
  acct 4   Y   999999999999 999999999999  0         2020-01-01 2030-12-31 2025-01-01 999999999999 0           10004   A0000000
  acct 5   Y   0            1000          0         2015-01-01 2020-06-15 2018-01-01 0            0           10005   A0000000
  acct 6   Y   0            100000        0         2020-01-01 2030-12-31 2025-01-01 0            0           10006   A0000000
} > "$OUT/acctdata.txt"

{
  xref 4000000000000001 1 1
  xref 4000000000000002 2 2
  xref 4000000000000003 3 3      # account 3 does not exist -> reason 101
  xref 4000000000000004 4 4
  xref 4000000000000005 5 5
  xref 4000000000000006 6 6
} > "$OUT/cardxref.txt"

{
  # account 6 has an existing category balance whose FILLER is non-blank
  tcat 6 01 0001 12345 "FILLERFILLERFILLERFILL"
  tcat 4 01 0001 99999999900 ""
} > "$OUT/tcatbal.txt"

{
  # 1: valid purchase, creates TCATBAL before any successful read (filler = spaces)
  tran T000000000000001 01 0001 12345    4000000000000001 "2024-03-01 10:00:00.000000"
  # 2: unknown card -> 100
  tran T000000000000002 01 0001 500      4999999999999999 "2024-03-01 10:01:00.000000"
  # 3: card known, account missing -> 101
  tran T000000000000003 01 0001 500      4000000000000003 "2024-03-01 10:02:00.000000"
  # 4: expired account, within limit -> 103
  tran T000000000000004 01 0001 500      4000000000000002 "2024-03-01 10:03:00.000000"
  # 5: over limit AND expired -> 103 (last check wins)
  tran T000000000000005 01 0001 5000     4000000000000005 "2024-03-01 10:04:00.000000"
  # 6: over limit only -> 102 (1000.00 limit, cyc credit 123.45 + 900.00)
  tran T000000000000006 01 0001 90000    4000000000000001 "2024-03-01 10:05:00.000000"
  # 7: negative amount (refund) -> cycle-debit path, existing TCATBAL with filler
  tran T000000000000007 01 0001 -2500    4000000000000006 "2024-03-01 10:06:00.000000"
  # 8: creation right after reading acct 6's record -> inherits "FILLER..." bytes
  tran T000000000000008 02 0002 1000     4000000000000001 "2024-03-01 10:07:00.000000"
  # 9: expiry equal to orig date -> passes (>= comparison)
  tran T000000000000009 01 0001 100      4000000000000005 "2020-06-15 23:59:59.000000"
  # 10: S9(09)V99 temp balance truncation: 9999999999.99 + 1.00 wraps to 0.99 -> passes;
  #     account ADDs wrap S9(10)V99, TCATBAL ADD wraps S9(09)V99
  tran T000000000000010 01 0001 100      4000000000000004 "2024-03-01 10:08:00.000000"
  # 11: negative zero amount
  tran T000000000000011 01 0001 -0       4000000000000001 "2024-03-01 10:09:00.000000"
} > "$OUT/dailytran.txt"

echo "edge data written to $OUT"
