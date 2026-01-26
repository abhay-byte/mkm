# Data Analysis & Graphing

Once you have your `cpu_efficiency_results.csv` or `gpu_efficiency_results.csv`, the final step is to visualize the data to find the efficiency "Sweet Spot".

## The Wattage x Score Plot

The user requested a "Wattage x Score" graph. This is typically an **XY Scatter Plot**:
- **X-Axis**: Power (Watts)
- **Y-Axis**: Score (Performance)

However, to see **Efficiency** clearly, you should also plot:
- **X-Axis**: Frequency
- **Y-Axis**: Efficiency (Score/Watt)

### Interpreting the Curves

1. **Linear Region**: At lower frequencies, Performance (Score) usually increases linearly with Power.
2. **Diminishing Returns (The "Knee")**: At a certain point, to get 10% more performance, you might need 50% more power. The curve flattens out.
3. **The Sweet Spot**: The particular Frequency where the **Efficiency (Score/Watt)** value is highest.

## How to Plot (Excel / Google Sheets)

1. Import the CSV file.
2. Select columns: `Frequency`, `Score`, and `Power`.
3. Create a **Combo Chart**:
   - **Series 1 (Bars)**: Power Consumption (Left Axis).
   - **Series 2 (Line)**: Score (Right Axis).
4. Create a second chart for **Efficiency**:
   - **X-Axis**: Frequency.
   - **Y-Axis**: Efficiency Column.

## Conclusion
- If your top 3 frequencies consume 30% more power but only provide a 2% score increase, **disable those top frequencies** in your custom kernel or governor settings.
- This gives you a "Cool & Efficient" device with barely noticeably performance loss.
