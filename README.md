# TUP Solver

## Traveling Umpire Problem Solver

Written by Túlio Toffolo and Tony Wauters.

(C) Copyright 2015, by CODeS Research Group, KU Leuven. All rights reserved.
More information: http://gent.cs.kuleuven.be/tup

Please address all contributions, suggestions, and inquiries to the current project administrator.

## Getting Started

The package be.kuleuven.codes.tup includes the solver source code.

The class with the main procedure is at be.kuleuven.codes.tup.bnb.Main.

## Getting Started

This repository contains stochastic local search (SLS) methods to address the unrelated parallel machine scheduling problem (UPMSP) with sequence dependent setup times.

### Compiling the code

This project uses [gradle](http://gradle.org "Gradle").
To generate the binaries, just run:

- gradle build

The jar file (tup.jar) will be generated.

### Usage examples:

- java -jar tup.jar umps14.txt 7 3 14_7_3.sol
- java -jar tup.jar umps14.txt 7 3 14_7_3.sol -threads 8 -time 4320 -ub 164440 

## Latest improvements

The current version includes (beta) support for parallel execution. 
The parameter "-threads N" indicates how many threads should be used by the parallel branch and bound.

## Requirements

Java 1.8 is required.

## Questions?

If you have any questions, please feel free to contact us.
For additional information, we would like to direct you to http://gent.cs.kuleuven.be/tup

Thanks!
