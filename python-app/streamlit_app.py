import streamlit as st
import pandas as pd
from iot_listener import latest_messages, start_listener
from streamlit_autorefresh import st_autorefresh
import plotly.express as px

# Set Streamlit layout
st.set_page_config(layout="wide")
st.title("📡 Azure IoT Real-time Task Dashboard")

# Start background listener once
if "started" not in st.session_state:
    start_listener()
    st.session_state.started = True

# Auto-refresh every 2 seconds
st_autorefresh(interval=2000, limit=None, key="refresh")

# Render dashboard if messages exist
if latest_messages:
    df = pd.DataFrame(latest_messages)
    df["timestamp"] = pd.to_datetime(df["timestamp"])

    # Remove cluster/routing if not Q2
    df.loc[df["q_class"] != "Q2", ["cluster", "routing_target"]] = ""

    # Assign colors by Q-class
    color_map = {"Q1": "green", "Q2": "blue", "Q3": "orange"}
    df["color"] = df["q_class"].map(color_map)

    # Section: Q-Class Summary
    st.markdown("### ✅ Number of Tasks by Q-Class")
    q_counts = df["q_class"].value_counts()
    col1, col2, col3 = st.columns(3)
    col1.metric("⚙️ Q1 (Motion)", q_counts.get("Q1", 0))
    col2.metric("👤 Q2 (Face)", q_counts.get("Q2", 0))
    col3.metric("📼 Q3 (Archive)", q_counts.get("Q3", 0))

    # Section: Delay trend
    col4, col5 = st.columns(2)
    with col4:
        st.markdown("### ⏱️ Delay Trend (ms)")
        st.line_chart(df.set_index("timestamp")["delay_ms"])

    # Section: Computational Load
    with col5:
        st.markdown("### 🧠 Computation Load Trend")
        st.line_chart(df.set_index("timestamp")["comp_load"])

    # Section: Cluster and Routing for Q2
    q2_df = df[df["q_class"] == "Q2"]
    if not q2_df.empty:
        st.markdown("### 🛰️ Cluster & Routing (Q2)")
        col6, col7 = st.columns(2)

        # Cluster bar chart
        with col6:
            cluster_counts = q2_df["cluster"].value_counts().reset_index()
            cluster_counts.columns = ["cluster", "count"]
            fig_cluster = px.bar(
                cluster_counts,
                x="cluster",
                y="count",
                color="cluster",
                color_discrete_sequence=px.colors.qualitative.Set2,
                title="Cluster Distribution (A / B)"
            )
            st.plotly_chart(fig_cluster, use_container_width=True)

        # Routing Target bar chart
        with col7:
            routing_counts = q2_df["routing_target"].value_counts().reset_index()
            routing_counts.columns = ["routing_target", "count"]
            fig_routing = px.bar(
                routing_counts,
                x="routing_target",
                y="count",
                color="routing_target",
                color_discrete_sequence=px.colors.qualitative.Dark2,
                title="Routing to Fog Nodes (d-0 ~ d-3)"
            )
            st.plotly_chart(fig_routing, use_container_width=True)
    else:
        st.info("No Q2 cluster data available.")

    # Section: Last 10 messages
    st.markdown("### 🔎 Last 10 Messages")
    st.dataframe(df.tail(10), use_container_width=True)

else:
    st.info("⏳ Waiting for incoming messages...")
