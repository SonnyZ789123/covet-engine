# The Pathcov pipeline

> First take a look at `/Users/yoran.mertens/dev/master-thesis/claude-context`. This contains the context about the whole project (CONTEXT.md) and how to work (HOW_TO_WORK.md).

## The workflow

![The pathcov pipeline](./jdart-pipeline.svg)

Input: the sut.jpf config that specifies the method under test, the general jdart.jpf settings, and some coverage heuristic config file specific when working with the coverage heuristic

1. We run JDart on the program, and JDart will perform concolic execution on the method specified in the sut.jpf. This will output execution paths, and record it's output. It will use the exploration and termination strategy specified in the configuration. The default for the exploration strategy is the coverage heuristic exploration strategy because that's is our thesis contribution.
2. The coverage heuristic gets the coverage block map from the pathcov in JSON format. It will use this to guide the exploration to uncovered nodes. 
3. When an itereation of execution is finished, we check if that path is already covered, if so we mark the path as `IGNORE`. JDart will update it's runtime coverage data to mark the nodes on the explored path as covered. This way the coverage data is kept up-to-date. 
4. Based on the execution paths (not `IGNORE` or `DONT_KNOW` paths), we generate the test suite. 

## Installation

JDart needs a Java 8 environment and other dependencies that I don't have installed locally. That's why we use a JDart image (for docker file see the coverage-guided-concolic-pipeline project). I currently have the `sonnyz789123/jdart-image:3.0.0` image installed. If not just ask me to provide an image. 
You can use that image to create a `jdart` container. When creating a container, it's important to bind-mount the config, sut and the local jdart. Because this pretty complex setup, just ask me to provide you the container. The coverage-guided-concolic-pipeline project provides scripts to run the docker container, and to generate the config files. 

## Development

In the `/Users/yoran.mertens/dev/master-thesis/suts` folder are programs I do testing on. When working on a feature. You can ask me to setup a dev program to test the feature. Then you can confirm your changes are working correctly by testing it on that dev program.

You have to make sure that the other exploration strategies (DFS and BFS keep working). 

You will have to go into the JDart container to run the jdart pipeline. You can do this with `docker exec -it jdart /bin/bash`. Remember to compile the project with `ant`, and then you can run the pipeline on the sut with `jdart-project/jpf-core/bin/jpf /configs/sut.jpf`. The files in sut.jpf are generated and are bind-mounted when starting the container. 

## Improvements

In the JDart pipeline I would like a more refined coverage heuristic, I get the coverage data from the pathcov pipeline. You can get the model of the coverage block map in the project intellij-coverage-model. This coverage block map is what the coverage heuristic model gets when the pathcov pipeline is finished. 

Currently the coverage heuristic is only node based. This means that only the nodes (blocks in the CFG) keep coverage information. In the most recent update of pathcov we added also edge coverage information (e.g. true or false branch is covered/uncovered). I want you to extend the current coverage tracking to include that edge infromation. 

There are 2 extension points: 
- when checking if the explored path is already covered, we should only check if the edges are covered. The nodes are essentially just a summary of the coverage of its branches. For example, a node (a block) is fully covered if its branches are covered, and its partially covered if one or more branches are covered but not all. And it's uncovered if no branches are covered. 
- We can do more in depth exploration guiding. We can extend/update how we keep track of the coverage. In the bytecode, we have explicit control when we need to execute a bytecode instruction. Ideally we could know which branches we already covered in our execution paths, and make the concolic execution explore uncovered branches. 