      ******************************************************************
      * Stand-in for the z/OS Language Environment service CEE3ABD so the
      * unmodified legacy batch programs link under GnuCOBOL. Terminates
      * the run unit with the requested abend code as the exit status.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. CEE3ABD.
       DATA DIVISION.
       LINKAGE SECTION.
       01  LS-ABCODE                    PIC S9(9) BINARY.
       01  LS-TIMING                    PIC S9(9) BINARY.
       PROCEDURE DIVISION USING LS-ABCODE, LS-TIMING.
           DISPLAY 'CEE3ABD: USER ABEND U' LS-ABCODE
           MOVE LS-ABCODE TO RETURN-CODE
           STOP RUN.
