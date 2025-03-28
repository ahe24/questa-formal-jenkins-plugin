# Questa Formal Jenkins Plugin

This is a Jenkins plugin that integrates Questa Formal static analysis results into Jenkins build reports.

## Features

*   Parses Questa Formal Lint and CDC (Clock Domain Crossing) reports.
*   Displays summary information about errors, warnings, and info messages from Lint reports.
*   Displays summary information about violations and cautions from CDC reports.
*   Provides detailed information about each check item in Lint reports.
*   Provides detailed information about each violation item in CDC reports.
*   Displays design quality score, timestamp, and design name from Lint reports.
*   Integrates with Jenkins build results to provide a clear overview of code quality.

## Installation

1.  Go to Jenkins > Manage Jenkins > Manage Plugins.
2.  Go to the "Advanced" tab.
3.  In the "Upload Plugin" section, upload the `.hpi` file of this plugin.
4.  Restart Jenkins.

## Usage

1.  In your Jenkins job configuration, add a new build step.
2.  Select "Add Questa Formal Results".
3.  Specify the paths to the Lint and CDC report files (default: `Lint_Results/lint.rpt` and `CDC_result/cdc.rpt`).
4.  Run the build.
5.  After the build, you can view the Questa Formal results in the build report.

## Report File Format

The plugin expects the following format for the Lint and CDC report files:

*   **Lint Report (`lint.rpt`)**:
    *   Summary information: `| Error (n)`, `| Warning (n)`, `| Info (n)`
    *   Detailed information: `Check: <name> [Category: <category>] (<count>) <details>`
    *   Metadata: `Design               : <design_name>`, `Timestamp            : <timestamp>`, `Design Quality Score : <score>`
*   **CDC Report (`cdc.rpt`)**:
    *   Summary information: `Violations (n)`, `Cautions (n)`
    *   Detailed information: `<name> (<count>) <details>`

## Development

1.  Clone this repository.
2.  Modify the source code.
3.  Build the plugin using Maven: `mvn clean install`
4.  Upload the generated `.hpi` file to Jenkins.

## Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues.

## License

[Insert License Information Here] (e.g., MIT License)
