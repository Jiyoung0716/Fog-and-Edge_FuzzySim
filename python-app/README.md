# Fog and Edge Computing – FuzzySim System

A hybrid **Java + Python-based simulation and real-time system** integrating **iFogSim** with **Azure IoT Hub** and a **Streamlit dashboard** for **fuzzy rule-based task offloading, clustering, and visualization**.

---

## 1. iFogSim (Java Simulation)

This module simulates an **intelligent task offloading strategy** in fog environments using **fuzzy classification**, **cluster-aware routing**, and **energy-efficient decision-making**.

### Logic Overview

#### Fuzzy Task Classification

Tasks are evaluated using 4 parameters:
- `Frame size (KB)`
- `Delay constraint (ms)`
- `Computational load (%)`
- `Bandwidth (Mbps)`

**Fuzzy rules** classify tasks into:
- `Q1` → Low-load tasks → processed **locally (on mobile)**
- `Q2` → Medium-load tasks → **offloaded to fog clusters**
- `Q3` → Heavy-load tasks → sent to **cloud**

#### Q2: Cluster Routing Logic
- Tasks in Q2 are routed to:
  - **Cluster A (d-0, d-1)** if closer to size ≈ 300KB
  - **Cluster B (d-2, d-3)** if closer to size ≈ 700KB
- Each cluster chooses the **lowest energy-consuming fog node**

#### Metrics Output
- Task assignment logs printed to console
- Summary of Q-class counts and routing shown

---

### Java Simulation with iFogSim
- First, download iFogSim's full files >> https://github.com/Cloudslab/iFogSim/archive/refs/heads/main.zip
- Install Java 8+
- Place `x24142816_FuzzySim.java` inside `src/org/fog/test/perfeval`
- Compile and run via Eclipse or command line
---

## 2. Azure IoT + Streamlit (Python)

### Three Components

#### 1) `main.py`
- Simulates **random camera streams** from 20 edge nodes (`m-i-j`)
- Performs the same **fuzzy classification logic**
- Sends structured JSON messages to Azure IoT Hub every 3 seconds
- Q2 tasks are routed to fog nodes (`d-0 ~ d-3`) based on clustering

#### 2) `iot_listener.py`
- Connects to Azure **Event Hub-compatible endpoint** with Event Hub-compatible connection string
- Receives all device-to-cloud (D2C) messages
- Stores the **100 most recent messages**
- Strips irrelevant fields (e.g., cluster info for Q1/Q3)

#### 3) `streamlit_app.py`
- real-time dashboard using **Streamlit + Plotly**
- Automatically refreshes every 3 seconds
-  **Q-class distribution** (Q1/Q2/Q3)
-  **Delay & Load trends** over time
-  **Cluster & routing (for Q2)** visualized by group(Cluser A,B) and node(d-0 to d-3)
-  **Recent 10 messages** by Q-class

---

## 3. Repository Structure

```
Fog-and-Edge_FuzzySim/
├── iFogSim-Simulation/
│   └── src/org/fog/test/perfeval/x24142816_FuzzySim.java
│   └── (Additional modified core utils if necessary)
├── python-app/
│   ├── main.py
│   ├── iot_listener.py
│   ├── streamlit_app.py
│   ├── requirements.txt
```

---

## 4. Deployment Options

### Option 1: Local
1. Run `main.py` to simulate devices
2. Run `streamlit run streamlit_app.py`
3. Open real-time dashboard automatically

---

## 5. Setup & Dependencies

### Python
```bash
pip install -r requirements.txt
```

---

## Author

**Jiyoung Kim**  
National College of Ireland
x24142816  
Fog & Edge Computing Project (2025)
