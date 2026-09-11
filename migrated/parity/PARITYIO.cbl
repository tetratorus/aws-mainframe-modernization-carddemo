      ******************************************************************
      * PARITYIO - test harness utility for the migration parity run.
      *
      * Moves data between the text (one record per line) files used by
      * app/data/ASCII and the migrated Java code, and the GnuCOBOL
      * indexed / fixed-length sequential files the unmodified legacy
      * program CBTRN02C reads and writes.
      *
      *   PARITYIO LOAD ACCT|XREF|TCAT     INFILE -> indexed file
      *   PARITYIO DUMP ACCT|XREF|TCAT|TRAN indexed file -> OUTFILE
      *   PARITYIO TOSEQ                   INFILE -> DALYTRAN (350 fixed)
      *   PARITYIO FROMSEQ                 DALYREJS (430 fixed) -> OUTFILE
      *
      * File names come from the environment (DD_INFILE, DD_OUTFILE,
      * DD_ACCTFILE, DD_XREFFILE, DD_TCATBALF, DD_TRANFILE, DD_DALYTRAN,
      * DD_DALYREJS), exactly as for CBTRN02C.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. PARITYIO.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT INFILE  ASSIGN TO INFILE
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS IS WS-IN-STATUS.
           SELECT OUTFILE ASSIGN TO OUTFILE
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS IS WS-OUT-STATUS.
           SELECT DALYTRAN-FILE ASSIGN TO DALYTRAN
                  ORGANIZATION IS SEQUENTIAL
                  FILE STATUS IS WS-SEQ-STATUS.
           SELECT DALYREJS-FILE ASSIGN TO DALYREJS
                  ORGANIZATION IS SEQUENTIAL
                  FILE STATUS IS WS-SEQ-STATUS.
           SELECT ACCOUNT-FILE ASSIGN TO ACCTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS DYNAMIC
                  RECORD KEY IS FD-ACCT-ID
                  FILE STATUS IS WS-IDX-STATUS.
           SELECT XREF-FILE ASSIGN TO XREFFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS DYNAMIC
                  RECORD KEY IS FD-XREF-CARD-NUM
                  FILE STATUS IS WS-IDX-STATUS.
           SELECT TCATBAL-FILE ASSIGN TO TCATBALF
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS DYNAMIC
                  RECORD KEY IS FD-TRAN-CAT-KEY
                  FILE STATUS IS WS-IDX-STATUS.
           SELECT TRANSACT-FILE ASSIGN TO TRANFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS DYNAMIC
                  RECORD KEY IS FD-TRANS-ID
                  FILE STATUS IS WS-IDX-STATUS.
       DATA DIVISION.
       FILE SECTION.
       FD  INFILE.
       01  IN-REC                       PIC X(430).
       FD  OUTFILE.
       01  OUT-REC                      PIC X(430).
       FD  DALYTRAN-FILE.
       01  DALYTRAN-REC                 PIC X(350).
       FD  DALYREJS-FILE.
       01  DALYREJS-REC                 PIC X(430).
       FD  ACCOUNT-FILE.
       01  FD-ACCTFILE-REC.
           05 FD-ACCT-ID                PIC 9(11).
           05 FD-ACCT-DATA              PIC X(289).
       FD  XREF-FILE.
       01  FD-XREFFILE-REC.
           05 FD-XREF-CARD-NUM          PIC X(16).
           05 FD-XREF-DATA              PIC X(34).
       FD  TCATBAL-FILE.
       01  FD-TRAN-CAT-BAL-RECORD.
           05 FD-TRAN-CAT-KEY           PIC X(17).
           05 FD-TRAN-CAT-DATA          PIC X(33).
       FD  TRANSACT-FILE.
       01  FD-TRANFILE-REC.
           05 FD-TRANS-ID               PIC X(16).
           05 FD-TRANS-DATA             PIC X(334).
       WORKING-STORAGE SECTION.
       01  WS-IN-STATUS                 PIC XX.
       01  WS-OUT-STATUS                PIC XX.
       01  WS-SEQ-STATUS                PIC XX.
       01  WS-IDX-STATUS                PIC XX.
       01  WS-ARGS                      PIC X(40).
       01  WS-MODE                      PIC X(8).
       01  WS-FILE                      PIC X(8).
       01  WS-EOF                       PIC X VALUE 'N'.
       01  WS-COUNT                     PIC 9(9) VALUE 0.
       PROCEDURE DIVISION.
           ACCEPT WS-ARGS FROM COMMAND-LINE
           UNSTRING WS-ARGS DELIMITED BY ALL SPACES
               INTO WS-MODE WS-FILE
           EVALUATE WS-MODE
               WHEN 'LOAD'    PERFORM DO-LOAD
               WHEN 'DUMP'    PERFORM DO-DUMP
               WHEN 'TOSEQ'   PERFORM DO-TOSEQ
               WHEN 'FROMSEQ' PERFORM DO-FROMSEQ
               WHEN OTHER
                   DISPLAY 'PARITYIO: bad arguments: ' WS-ARGS
                   MOVE 8 TO RETURN-CODE
           END-EVALUATE
           GOBACK.

       DO-LOAD.
           OPEN INPUT INFILE
           PERFORM CHECK-IN
           EVALUATE WS-FILE
               WHEN 'ACCT' OPEN OUTPUT ACCOUNT-FILE
               WHEN 'XREF' OPEN OUTPUT XREF-FILE
               WHEN 'TCAT' OPEN OUTPUT TCATBAL-FILE
               WHEN OTHER  PERFORM BAD-FILE
           END-EVALUATE
           PERFORM CHECK-IDX
           PERFORM UNTIL WS-EOF = 'Y'
               MOVE SPACES TO IN-REC
               READ INFILE
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       ADD 1 TO WS-COUNT
                       EVALUATE WS-FILE
                           WHEN 'ACCT'
                               WRITE FD-ACCTFILE-REC FROM IN-REC
                           WHEN 'XREF'
                               WRITE FD-XREFFILE-REC FROM IN-REC
                           WHEN 'TCAT'
                               WRITE FD-TRAN-CAT-BAL-RECORD FROM IN-REC
                       END-EVALUATE
                       PERFORM CHECK-IDX
               END-READ
           END-PERFORM
           CLOSE INFILE
           EVALUATE WS-FILE
               WHEN 'ACCT' CLOSE ACCOUNT-FILE
               WHEN 'XREF' CLOSE XREF-FILE
               WHEN 'TCAT' CLOSE TCATBAL-FILE
           END-EVALUATE
           DISPLAY 'PARITYIO LOAD ' WS-FILE ' ' WS-COUNT.

       DO-DUMP.
           OPEN OUTPUT OUTFILE
           PERFORM CHECK-OUT
           EVALUATE WS-FILE
               WHEN 'ACCT' OPEN INPUT ACCOUNT-FILE
               WHEN 'XREF' OPEN INPUT XREF-FILE
               WHEN 'TCAT' OPEN INPUT TCATBAL-FILE
               WHEN 'TRAN' OPEN INPUT TRANSACT-FILE
               WHEN OTHER  PERFORM BAD-FILE
           END-EVALUATE
           PERFORM CHECK-IDX
           PERFORM UNTIL WS-EOF = 'Y'
               EVALUATE WS-FILE
                   WHEN 'ACCT'
                       READ ACCOUNT-FILE NEXT
                           AT END MOVE 'Y' TO WS-EOF
                           NOT AT END
                               WRITE OUT-REC FROM FD-ACCTFILE-REC
                       END-READ
                   WHEN 'XREF'
                       READ XREF-FILE NEXT
                           AT END MOVE 'Y' TO WS-EOF
                           NOT AT END
                               WRITE OUT-REC FROM FD-XREFFILE-REC
                       END-READ
                   WHEN 'TCAT'
                       READ TCATBAL-FILE NEXT
                           AT END MOVE 'Y' TO WS-EOF
                           NOT AT END
                               WRITE OUT-REC FROM FD-TRAN-CAT-BAL-RECORD
                       END-READ
                   WHEN 'TRAN'
                       READ TRANSACT-FILE NEXT
                           AT END MOVE 'Y' TO WS-EOF
                           NOT AT END
                               WRITE OUT-REC FROM FD-TRANFILE-REC
                       END-READ
               END-EVALUATE
               IF WS-EOF = 'N'
                   ADD 1 TO WS-COUNT
                   PERFORM CHECK-OUT
               END-IF
           END-PERFORM
           EVALUATE WS-FILE
               WHEN 'ACCT' CLOSE ACCOUNT-FILE
               WHEN 'XREF' CLOSE XREF-FILE
               WHEN 'TCAT' CLOSE TCATBAL-FILE
               WHEN 'TRAN' CLOSE TRANSACT-FILE
           END-EVALUATE
           CLOSE OUTFILE
           DISPLAY 'PARITYIO DUMP ' WS-FILE ' ' WS-COUNT.

       DO-TOSEQ.
           OPEN INPUT INFILE
           PERFORM CHECK-IN
           OPEN OUTPUT DALYTRAN-FILE
           PERFORM CHECK-SEQ
           PERFORM UNTIL WS-EOF = 'Y'
               MOVE SPACES TO IN-REC
               READ INFILE
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       ADD 1 TO WS-COUNT
                       WRITE DALYTRAN-REC FROM IN-REC
                       PERFORM CHECK-SEQ
               END-READ
           END-PERFORM
           CLOSE INFILE DALYTRAN-FILE
           DISPLAY 'PARITYIO TOSEQ ' WS-COUNT.

       DO-FROMSEQ.
           OPEN INPUT DALYREJS-FILE
           PERFORM CHECK-SEQ
           OPEN OUTPUT OUTFILE
           PERFORM CHECK-OUT
           PERFORM UNTIL WS-EOF = 'Y'
               READ DALYREJS-FILE
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       ADD 1 TO WS-COUNT
                       WRITE OUT-REC FROM DALYREJS-REC
                       PERFORM CHECK-OUT
               END-READ
           END-PERFORM
           CLOSE DALYREJS-FILE OUTFILE
           DISPLAY 'PARITYIO FROMSEQ ' WS-COUNT.

       BAD-FILE.
           DISPLAY 'PARITYIO: bad file argument: ' WS-FILE
           MOVE 8 TO RETURN-CODE
           STOP RUN.

       CHECK-IN.
           IF WS-IN-STATUS NOT = '00'
               DISPLAY 'PARITYIO: INFILE status ' WS-IN-STATUS
               MOVE 12 TO RETURN-CODE
               STOP RUN
           END-IF.
       CHECK-OUT.
           IF WS-OUT-STATUS NOT = '00'
               DISPLAY 'PARITYIO: OUTFILE status ' WS-OUT-STATUS
               MOVE 12 TO RETURN-CODE
               STOP RUN
           END-IF.
       CHECK-SEQ.
           IF WS-SEQ-STATUS NOT = '00'
               DISPLAY 'PARITYIO: sequential file status ' WS-SEQ-STATUS
               MOVE 12 TO RETURN-CODE
               STOP RUN
           END-IF.
       CHECK-IDX.
           IF WS-IDX-STATUS NOT = '00'
               DISPLAY 'PARITYIO: indexed file ' WS-FILE
                       ' status ' WS-IDX-STATUS
               MOVE 12 TO RETURN-CODE
               STOP RUN
           END-IF.
