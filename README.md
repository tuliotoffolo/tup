# TUP Solver

## Traveling Umpire Problem Solver

Written by Túlio Toffolo (parts of the local search were coded by Tony Wauters).

(C) Copyright 2015, by CODeS Research Group, KU Leuven. All rights reserved.  
More information: <a href="http://gent.cs.kuleuven.be/tup" target="_blank">http://gent.cs.kuleuven.be/tup</a>

Please address all contributions, suggestions, and inquiries to the current project administrator.

## Getting Started

The package be.kuleuven.codes.tup includes the solver source code. 
The main class for the branch-and-bound with decomposition-based lower bounds is __be.kuleuven.codes.tup.bnb.Main__, while the main class for the decomposition-based heuristic is __be.kuleuven.codes.tup.heuristic_decomp.Main__.

### Compiling the code

This project uses [gradle](http://gradle.org "Gradle").
To generate the binaries, just run:

- gradle build

Two jar files (__**tup.jar**__ and __**tup-heuristic.jar**__) will be generated.

### Usage examples:

Branch-and-bound with decomposition-based lower bounds:

```
Usage: java -jar tup.jar <instance> <q1> <q2> <output> [options]
    <instance>   : Instance name (example: umps14.dat).
    <q1>         : Value for the q1 parameter (example: 7).
    <q2>         : Value for the q2 parameter  (example: 3).
    <output>     : Output solution file (example: umps14_7_3.sol).

Options:
    -no-windows  : Run the lower bound without multiple time windows.
    -threads <n> : Maximum number n of threads (default: number of CPUs).
    -time <time> : Time limit, in minutes (default: 4320).
    -ub <ub>     : Initial upper bound (default: unbounded).

Examples:
    java -jar tup.jar umps_14.txt 7 3 umps_14_7_3.sol
    java -jar tup.jar umps_14.txt 7 3 umps_14_7_3.sol -threads 8 -time 4320 -ub 164440
```

Decomposition-based heuristic:

```
Usage: java -jar tup-heuristic.jar <instance> <q1> <q2> <output> [options]
    <instance>   : Instance name (example: umps14.dat).
    <q1>         : Value for the q1 parameter (example: 7).
    <q2>         : Value for the q2 parameter  (example: 3).
    <output>     : Output solution file (example: umps14_7_3.sol).

Options:
    -n <n>       : Value for algorithm parameter 'n' (default: 4).
    -step <s>    : Value for algorithm parameter 'step' (default: 2).
    -penalty <p> : Value for algorithm parameter 'penalty' (default: 0).
    -init <file> : Load initial solution from file.
    -threads <t> : Maximum number of threads to use (default: number of CPUs).
    -time <time> : Runtime limit (in seconds) (default: 10800).

Examples:
    java -jar tup.jar umps_30.txt 5 5 umps_14_7_3.sol
    java -jar tup.jar umps_30.txt 5 5 umps_14_7_3.sol -n 5 -step 2
    java -jar tup.jar umps_30.txt 5 5 umps_14_7_3.sol -n 5 -step 2 -ini umps_30_5_5.sol
```


## Requirements

Java 1.8 is required.  
The decomposition-based heuristic requires cplex or gurobi. Make sure the file cplex.jar or gurobi.jar are in your classpath before executing the code.

## Questions?

If you have any questions, please feel free to contact us.
For additional information, we would like to direct you to http://gent.cs.kuleuven.be/tup

Thanks!
