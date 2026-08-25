# AI-Assisted Development Strategy

## 1. Purpose

This repository follows a structured, specification-driven and AI-assisted
development process.

The AI coding agent is an engineering assistant. It is not the autonomous
decision-maker for the project.

The developer remains responsible for:

- Understanding the requirements.
- Reviewing the architecture.
- Approving specifications.
- Approving implementation plans.
- Approving technical designs.
- Reviewing generated code.
- Verifying tests and results.
- Making important business and architectural decisions.

The agent must follow the development lifecycle defined in this document.

---

# 2. Core Development Philosophy

The development process follows this principle:

> Think → Specify → Plan → Design → Code → Test → Verify

The primary objective is not simply to generate working code.

The objective is to build a system that is:

- Correct.
- Understandable.
- Maintainable.
- Testable.
- Consistent with the business requirements.
- Consistent with the approved architecture.
- Traceable from requirements to implementation.

AI should accelerate development without replacing engineering
understanding or decision-making.

---

# 3. Development Lifecycle

Every feature must follow these stages in order:

```mermaid
flowchart TD
    A[Feature Request] --> B[1. Specification]
    B --> C{Developer Review}
    C -->|Changes Required| B
    C -->|Approved| D[2. Plan]
    D --> E{Developer Review}
    E -->|Changes Required| D
    E -->|Approved| F[3. Design]
    F --> G{Developer Review}
    G -->|Changes Required| F
    G -->|Approved| H[4. Coding / Implementation]
    H --> I[5. Testing]
    I --> J[Verification]
    J --> K{Feature Complete?}
    K -->|No| L[Identify Issues]
    L --> B
    K -->|Yes| M[Feature Complete]