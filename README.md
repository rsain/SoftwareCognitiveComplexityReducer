# Cognitive Complexity Reduction Tool

This Eclipse Plug-in automates the detection and refactoring of Java methods with high Cognitive Complexity. It functions as a pipeline that scans a target project, identifies methods exceeding a configured complexity threshold, and suggests "Extract Method" refactorings to improve maintainability.



The tool is designed to run as a headless Eclipse Application, making it suitable for integration into build systems, CI/CD pipelines, or batch analysis workflows.

## Publications & Reproducibility

This repository contains the evolving codebase for our refactoring research. If you are looking for the source code associated with specific publications, please refer to the following versions:

* **Current Version (v2.0):** Features a new pipeline architecture and multiple solvers, including an exact Integer Linear Programming (ILP) solver. 
* **Legacy Version (v1.0):** Features the original approach using an exhaustive search as solver.
  * **Source Code:** Download the exact v1.0 release from the GitHub Releases page.

## Features

* **Automated Project Scanning:** Recursively parses the specified Eclipse project to locate Java methods.
* **Complexity Filtering:** Automatically filters methods that exceed the defined cognitive complexity threshold.
* **Multiple Refactoring Solvers:** Offers both exact optimization (ILP) and exhaustive search strategies to generate refactoring solutions.
* **Refactoring Pipeline:** Orchestrates the refactoring logic via `MethodRefactoringPipeline`, connecting the analysis phase with the selected solver.
* **CSV Reporting:** Outputs a detailed CSV report containing the analysis results and refactoring outcomes for every processed method.
* **Headless Execution:** Implements `IApplication` to run without the Eclipse UI overhead.

## System Requirements

* **Java Runtime:** JDK 11 or higher.
* **Eclipse Platform:** Eclipse IDE for RCP and RAP Developers (4.20+) or any Eclipse distribution containing the Plugin Development Environment (PDE) and JDT.
* **Workspace:** The target project to be analyzed must exist in an Eclipse workspace (different from the one where this plugin project is located).
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
3. Edit the file to specify your local output directory, complexity thresholds, and document your CPLEX native library path.

### Command-Line Arguments
The `Config` class parses the runtime arguments passed to the Eclipse Application.

1. **Project Name (Required):** The exact name of the target project in the external Eclipse workspace.
2. **Solver Type (Optional):** The key selecting the desired algorithm for calculating the refactoring solution. Available options are:
   * `ILP`: Integer Linear Programming solver for exact optimization.
   * `ES-LSF`: Exhaustive Search heuristic that prioritizes longest sequences first.
   * `ES-SSF`: Exhaustive Search heuristic that prioritizes shortest sequences first.

## Usage: Running Headless

1. **Create Run Configuration:** Go to **Run > Run Configurations...**, right-click **Eclipse Application**, and select **New**.
2. **Main Tab Setup:** Name the configuration (e.g., `ReduceComplexity-Headless`). Select "Run an application" and choose `neo.reducecognitivecomplexity.app.Application`. Set the **Workspace Data** location to point to the workspace containing the target project to be analyzed.
3. **Arguments Tab Setup:** * **Program Arguments:** Enter the target project and the chosen solver key (e.g., `MyLegacyProject ILP`).
   * **VM Arguments:** You **must** define the native library path for CPLEX, alongside any memory adjustments. For example:
     ```text
     -Djava.library.path="/path/to/cplex/bin/your_os_arch" -Xmx4G
     ```
     *(Note: Replace `/path/to/cplex/bin/your_os_arch` with your actual CPLEX bin directory).*
4. **Working Directory Setup (Crucial):** Still in the **Arguments** tab, scroll down to the **Working directory** section. 
   * Change the selection from "Default" to **Other**.
   * Click the **Workspace...** button.
   * Select this plugin project (the folder containing your `config.properties` file) and click **OK**. *This ensures the application finds your configuration file instead of falling back to default Eclipse paths.*
5. **Run:** Click Apply and then Run to execute the configuration.

## Output

The application generates a CSV file in the output folder defined in your `config.properties`. 

**File Naming:** `[ProjectName].csv`

**Content:** The CSV contains records for every method processed, detailing the method, location, initial Cognitive Complexity, suggested refactoring, and resulting complexity.

## Architecture

The project follows a pipeline architecture separating the application layer, core logic, and input/output.

### 1. Application Layer (`neo.reducecognitivecomplexity.app`)
* **`Application.java`:** The entry point implementing `IApplication`. It initializes the environment, disables auto-builds for performance, and triggers the analysis.
* **`Config.java`:** Handles parsing and validation of command-line arguments.
* **`Constants.java`:** Centralizes static definitions and dynamically loads environment variables from `config.properties`.

### 2. Core Logic (`neo.reducecognitivecomplexity.core`)
* **`MethodRefactoringPipeline.java`:** The central coordinator. It receives a method, determines the best refactoring strategy via the solver, and suggests the changes.
* **`solvers.SolverType`:** Defines the supported optimization strategies (`ILP`, `ES-LSF`, `ES-SSF`).

### 3. JDT Integration (`neo.reducecognitivecomplexity.core.jdt`)
* **`JavaMethodProcessor.java`:** Uses Eclipse JDT to traverse the project's AST (Abstract Syntax Tree), calculate metrics, and identify methods requiring refactoring.

### 4. Input/Output (`neo.reducecognitivecomplexity.core.io`)
* **`CsvResultWriter.java`:** Manages structured output, ensuring thread-safe writing of analysis results to the file system.

## License

This project is distributed under the GPL-3.0 License.