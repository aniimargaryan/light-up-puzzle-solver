Light Up / Akari Puzzle Solver
==============================
AUTHORS: Ani Margaryan, Ina Grigoryan, Gayane Baghdasaryan
GROUP NAME: AI+G

NOTE: Source files were generated with AI assistance (ChatGPT)
based on a solution description provided by the authors.

FILES
-----
  LightUpCSPSolver.java         - Backtracking CSP solver
  LightUpLocalSearchSolver.java - Simulated annealing solver
  LightUpGUI.java               - Swing GUI using both solvers

REQUIREMENTS
------------
  Java 17 or later

COMPILE
-------
  javac *.java

RUN
---
  CSP solver:
    java LightUpCSPSolver puzzle.txt

  Local search solver:
    java LightUpLocalSearchSolver puzzle.txt
    java LightUpLocalSearchSolver puzzle.txt --restarts 200 --steps 300000 --seed 42

  GUI:
    java LightUpGUI

PUZZLE FORMAT
-------------
  Optional first line: R C (rows and columns)
  Then R rows using:
    .   white cell
    #   black wall
    0-4 numbered black wall

  Example:
    5 5
    ..#..
    .2...
    ..#..
    ...1.
    ..#..

TEST CASES
----------
  Example puzzles are provided in the tests/ folder:
    tests/easy/            - easy puzzles
    tests/medium/          - medium puzzles
    tests/hard/            - hard puzzles
    tests/unsolvable_trap/ - puzzles with unsolvable and trap (very hard) puzzles

  Usage example:
    java LightUpCSPSolver tests/easy/easy_05.txt
