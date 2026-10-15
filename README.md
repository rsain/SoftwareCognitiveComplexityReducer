# Cognitive Complexity Reduction Tool

This Eclipse Plug-in automates the detection and refactoring of Java methods with high Cognitive Complexity. It functions as a pipeline that scans a target project, identifies methods exceeding a configured complexity threshold, suggests "Extract Method" refactorings to improve maintainability, and can automatically apply saved refactoring solutions back to the source code.

The tool is designed to run as a headless Eclipse Application, making it suitable for integration into build systems, CI/CD pipelines, or large-scale batch analysis workflows.

## Publications & Reproducibility

This repository contains the evolving codebase for our refactoring research. If you are looking for the source code associated with specific publications, please refer to the following versions:

* **Current Version (v2.0):** Features a new pipeline architecture, batch processing capabilities, includes an exact Integer Linear Programming (ILP) solver, and an automated solution applier.
* **Legacy Version (v1.0):** Features the original approach using an exhaustive search as solver.
  * **Paper**: R. Saborido, J. Ferrer, F. Chicano and E. Alba, "Automatizing Software Cognitive Complexity Reduction," in IEEE Access, vol. 10, pp. 11642-11656, 2022, doi: [10.1109/ACCESS.2022.3144743](https://ieeexplore.ieee.org/document/9686676). 
  * **Source Code:** [Download the exact v1.0 release from the GitHub Releases page](https://github.com/rsain/SoftwareCognitiveComplexityReducer/releases/tag/IEEE_Access-Automatizing_Software_Cognitive_Complexity_Reduction).

## Features

* **Automated Project Scanning:** Recursively parses the specified Eclipse project to locate Java methods.
* **Batch Processing Mode:** Bypasses full project scanning to process a predefined list of targeted methods from an input CSV, ideal for large empirical studies across multiple projects. 
* **Complexity Filtering:** Automatically filters methods that exceed the defined cognitive complexity threshold.
* **Multiple Refactoring Solvers:** Offers both exact optimization (ILP) and exhaustive search strategies to generate refactoring solutions. 
* **Refactoring Pipeline:** Orchestrates the refactoring logic via `MethodRefactoringPipeline`, connecting the analysis phase with the selected solver.
* **Saved Solution Application:** Applies saved optimal refactoring solutions (`.results` files) directly to workspace source code deterministically using Eclipse JDT.
* **CSV Reporting:** Outputs a detailed CSV report containing the analysis results and refactoring outcomes for every processed method.
* **Headless Execution:** Implements `IApplication` to run without the Eclipse UI overhead.

## System Requirements

* **Java Runtime:** JDK 11 or higher.
* **Eclipse Platform:** Eclipse IDE for RCP and RAP Developers (4.20+) or any Eclipse distribution containing the Plugin Development Environment (PDE) and JDT.
* **Workspace:** The target project(s) to be analyzed or modified must exist in an Eclipse workspace (different from the one where this plugin project is located).
* **IBM ILOG CPLEX:** The ILP solver requires CPLEX Optimization Studio (specifically the `cplex.jar` and its native system libraries). 

## Installation

1. **Clone the Repository:** Download the source code to your local machine.
2. **Configure CPLEX Dependency:** Due to licensing restrictions, the `cplex.jar` binary is not included in this repository. You must install CPLEX locally, then right-click the project in Eclipse -> **Build Path** -> **Configure Build Path...** -> **Libraries** -> **Add External JARs...** and select your local `cplex.jar`.
3. **Import into Eclipse:** Navigate to File > Import > General > Existing Projects into Workspace, and select the root directory of the cloned repository.
4. **Resolve Dependencies:** Open `plugin.xml` or `MANIFEST.MF` and ensure all Eclipse dependencies (e.g., `org.eclipse.jdt.core`, `org.eclipse.equinox.app`) are resolved via the Target Platform.

## Configuration

The application separates environment-specific configurations from core execution arguments.

### Properties File
1. Locate the `config.template.properties` file in the project root.
2. Copy and rename it to `config.properties`.
3. Edit the file to specify your environment settings. The key properties include:
   * `output.folder`: Absolute path to the directory where the output CSV files will be saved (e.g., `./output`).
   * `complexity.threshold`: The cognitive complexity score required to trigger an analysis on a method (e.g., `15`).
   * `cplex.native.library.path`: The absolute path to your local CPLEX native binaries (e.g., `/path/to/cplex/bin/your_os_arch`).
   * `working.memory`: Maximum memory (in Megabytes) solvers as CPLEX are allowed to use. Setting this prevents `OutOfMemoryError` crashes during executions by forcing the solver to write to disk or abort if a single method's extraction model becomes too complex.

### Command-Line Arguments

#### Mode 1: Solution Generation (`neo.reducecognitivecomplexity.app.Application`)
Analyzes methods exceeding the cognitive complexity threshold and generates `.results` solution files. This mode can be executed in two sub-modes:

* **Single Project Sub-Mode** (scans an entire project):
  1. **Project Name (Required):** Target project name in the external workspace.
  2. **Solver Type (Optional):** Algorithm key (`ILP`, `ES-LSF`, `ES-SSF`). Default: `ILP`.
  * *Program Arguments Example:* `MyTargetProject ILP`

* **Batch Sub-Mode** (processes a target list of methods across projects):
  1. **Batch Flag & Input File (Required):** `-batch <path_to_input_csv>` pointing to a targeted method list CSV.
  2. **Solver Type (Optional):** Algorithm key (`ILP`, `ES-LSF`, `ES-SSF`). Default: `ILP`.
  * *Program Arguments Example:* `-batch /path/to/methods.csv ILP`

#### Mode 2: Applying Saved Solutions (`neo.reducecognitivecomplexity.app.ApplySolutions`)
1. **Results Folder Path (Required):** The absolute or workspace-relative path to the directory containing `.results` solution files to be applied. It just applies refactoring operations which are optimal (solved by the `ÌLP` solver and with status `Optimal`, others are discarded). Note that `.results` files are generated by Mode 1.

## Usage: Running Headless

### Option A: Running Analysis & Solution Generation
1. **Create Run Configuration:** Go to **Run > Run Configurations...**, right-click **Eclipse Application**, and select **New**.
2. **Main Tab Setup:** Name the configuration (e.g., `ReduceComplexity-Headless`). Select "Run an application" and choose `neo.reducecognitivecomplexity.app.Application`. Set the **Workspace Data** location to point to the workspace containing the target project(s) to be analyzed.
3. **Arguments Tab Setup:**
   * **Program Arguments:**
     * *Single Project Mode:* Enter the target project and the chosen solver key (e.g., `MyLegacyProject ILP`).
     * *Batch Mode:* Enter the batch flag/file and the solver key (e.g., `-batch /path/to/input_methods.csv ILP`).
   * **VM Arguments:** You **must** define the native library path for CPLEX, alongside sufficient JVM heap memory to parse large ASTs. For example:
     ```text
     -Djava.library.path="/path/to/cplex/bin/your_os_arch" -Xmx8G
     ```
     *(Note: Replace `/path/to/cplex/bin/your_os_arch` with your actual CPLEX bin directory. Ensure your `-Xmx` heap size is comfortably larger than the `working.memory` limit defined in your properties file).*
4. **Working Directory Setup (Crucial):** Still in the **Arguments** tab, scroll down to the **Working directory** section. 
   * Change the selection from "Default" to **Other**.
   * Click the **Workspace...** button.
   * Select this plugin project (the folder containing your `config.properties` file) and click **OK**. *This ensures the application finds your configuration file instead of falling back to default Eclipse paths.*
5. **Run:** Click Apply and then Run to execute the configuration.

### Option B: Applying Saved Refactorings
1. **Create Run Configuration:** Go to **Run > Run Configurations...**, right-click **Eclipse Application**, and select **New**.
2. **Main Tab Setup:** Name the configuration (e.g., `ApplySolutions-Headless`). Select "Run an application" and choose `neo.reducecognitivecomplexity.app.ApplySolutions`. Set the **Workspace Data** location to point to the workspace containing the target project(s) to be refactored.
3. **Arguments Tab Setup:**
   * **Program Arguments:** Pass the path to the folder containing `.results` solution files:
     ```text
     /path/to/results_folder
     ```
4. **Run:** Click Apply and then Run to execute the AST transformations and apply refactorings to the source code.

## Output

The application generates output files in the configured output or results directory:

* **Analysis Mode:** Generates a CSV file (`[ProjectName].csv` or `[BatchFileName]_results.csv`) detailing initial Cognitive Complexity, suggested refactoring sequences, solver metrics, and predicted complexity reductions.
* **Refactoring Application Mode:** Modifies target Java source files in place within the target workspace, generating execution logs (`execution.log`) and a summary report (`execution_results.csv`) detailing success and failure metrics for each extraction.

## Architecture

The project follows a pipeline architecture separating the application layer, core logic, and input/output.

### 1. Application Layer (`neo.reducecognitivecomplexity.app`)
* **`Application.java`:** Entry point for method detection, analysis, and solution generation.
* **`ApplySolutions.java`:** Entry point for applying saved `.results` refactoring solutions directly to workspace source files using Eclipse JDT.
* **`Config.java`:** Handles parsing and validation of command-line arguments.
* **`Constants.java`:** Centralizes static definitions and dynamically loads environment variables from `config.properties`.

### 2. Core Logic (`neo.reducecognitivecomplexity.core`)
* **`MethodRefactoringPipeline.java`:** The central coordinator for analysis and solver selection.
* **`SavedSolutionProcessor.java`:** Executes batch refactoring transformations deterministically with position tracking and error handling.
* **`BatchCsvProcessor.java`:** Handles the iterative processing of pre-defined methods supplied via an input CSV.
* **`solvers.SolverType`:** Defines the supported optimization strategies (`ILP`, `ES-LSF`, `ES-SSF`).

### 3. JDT Integration (`neo.reducecognitivecomplexity.core.jdt`)

* **`JavaMethodProcessor.java`:** Uses Eclipse JDT to traverse the project's AST (Abstract Syntax Tree), calculate metrics, and identify methods requiring refactoring.

### 4. Input/Output (`neo.reducecognitivecomplexity.core.io`)
* **`CsvResultWriter.java`:** Manages structured output, ensuring thread-safe writing of analysis results to the file system.

## License

This project is distributed under the GPL-3.0 License.