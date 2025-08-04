# ☁️ Fog and Edge Computing – FuzzySim System

A hybrid **Java + Python-based simulation and real-time system** integrating **iFogSim** with **Azure IoT Hub** and a **Streamlit dashboard** for **fuzzy rule-based task offloading, clustering, and visualization**.

---

## 🧪 iFogSim (Java Simulation)

This module simulates an **intelligent task offloading strategy** in fog environments using **fuzzy classification**, **cluster-aware routing**, and **energy-efficient decision-making**.

### ⚙️ Logic Overview

#### Fuzzy Task Classification

Tasks are evaluated using 4 parameters:
- 📦 `Frame size (KB)`
- ⏱️ `Delay constraint (ms)`
- 🧠 `Computational load (%)`
- 📶 `Bandwidth (Mbps)`

**Fuzzy rules** classify tasks into:
- `Q1` → Low-load tasks → processed **locally (on mobile)**
- `Q2` → Medium-load tasks → **offloaded to fog clusters**
- `Q3` → Heavy-load tasks → sent to **cloud**

#### Q2: Cluster Routing Logic
- Tasks in Q2 are routed to:
  - **Cluster A (d-0, d-1)** if closer to size ≈ 300KB
  - **Cluster B (d-2, d-3)** if closer to size ≈ 700KB
- Each cluster chooses the **lowest energy-consuming fog node**

✅ Simulates K-means-like logic with energy awareness  
✅ Fog cluster membership is pre-defined

#### Metrics Output
- Task assignment logs printed to console
- Summary of Q-class counts and routing shown

---

## 🌐 Azure IoT + Streamlit (Python)

Python-based real-time pipeline mimicking live IoT task flows into Azure cloud.

### 📡 Components

#### `main.py`
- Simulates **random camera streams** from 20 edge nodes (`m-i-j`)
- Performs the same **fuzzy classification logic**
- Sends structured JSON to Azure IoT Hub every 3 seconds
- Q2 tasks are routed to fog nodes (`d-0 ~ d-3`) based on clustering

#### `iot_listener.py`
- Connects to Azure **Event Hub-compatible endpoint**
- Receives all device-to-cloud (D2C) messages
- Stores the **100 most recent messages**
- Strips irrelevant fields (e.g., cluster info for Q1/Q3)

#### `streamlit_app.py`
- Beautiful real-time dashboard using **Streamlit + Plotly**
- Automatically refreshes every 2 seconds
- ✅ **Q-class distribution** (Q1/Q2/Q3)
- 📉 **Delay & Load trends** over time
- 🛰️ **Cluster & routing (for Q2)** visualized by group and node
- 📋 **Recent 10 messages** styled by Q-class (color-coded)

---

## 🧰 Repository Structure

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

## 🚀 Deployment Options

### Option 1: Local
1. Run `main.py` to simulate devices
2. Run `streamlit run streamlit_app.py`
3. Observe real-time dashboard

### Option 2: Docker (for dashboard only)
Create a `Dockerfile` for `python-app/` and deploy containerized Streamlit.

---

## 📦 Setup & Dependencies

### Python
```bash
pip install -r requirements.txt
```

### Java
- Java 8+
- iFogSim base project (clone from original repository)
- Place `x24142816_FuzzySim.java` inside `src/org/fog/test/perfeval`
- Compile and run via Eclipse or command line

---

## 📝 Notes

- This project demonstrates both **discrete-time simulation (iFogSim)** and **real-time device simulation (Azure IoT)** for fog computing evaluation
- Focus is placed on **task heterogeneity**, **network-aware routing**, and **fuzzy offloading**
- Can be extended to include **reinforcement learning**, **adaptive scheduling**, or **multi-application support**

---

## 👩‍💻 Author

**Jiyoung Kim**  
x24142816  
Fog & Edge Computing Project (2025)
