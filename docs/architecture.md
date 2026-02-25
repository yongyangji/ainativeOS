# AI-Native OS Technical Architecture (Java + MySQL)

## Control Plane
- Java 21 + Spring Boot 3
- REST endpoints for Goal planning and execution
- Semantic Kernel service orchestrates GoalSpec -> AtomicOp pipeline

## Unified Abstraction
- Resources are represented by capability-oriented atomic operations
- Provider mapping stage is part of execution plan, independent from OS or cloud vendor

## Self-healing VFS Contract
- Structured failure object includes context stack and error vectors
- Failure object is machine-readable and designed for automatic retry policy

## Stateless Runtime Direction
- DesiredState object declares target runtime state
- Current MVP keeps runtime declarative model in domain layer, ready for WASM/container adapters

## Data Layer
- MySQL 8.4
- `goal_execution` persistence for execution records
