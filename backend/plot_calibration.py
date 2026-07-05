import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

def main():
    try:
        df = pd.read_csv('calibration_results.csv')
    except FileNotFoundError:
        print("Error: calibration_results.csv not found.")
        return

    if df.empty:
        print("Error: CSV is empty.")
        return

    # Create 10 buckets (0-10%, 10-20%, etc.)
    bins = np.linspace(0, 1, 11)
    df['bucket'] = pd.cut(df['predicted_prob'], bins=bins, include_lowest=True)
    
    # Calculate actual win rate per bucket
    calibration = df.groupby('bucket', observed=False)['actual_win'].mean().reset_index()
    # Get midpoints for plotting
    calibration['midpoint'] = calibration['bucket'].apply(lambda x: x.mid)
    
    # Plot
    plt.figure(figsize=(8, 6))
    plt.plot([0, 1], [0, 1], 'k--', label='Perfect Calibration')
    
    # Drop NaN values (buckets with no data)
    valid_cal = calibration.dropna(subset=['actual_win'])
    
    plt.plot(valid_cal['midpoint'], valid_cal['actual_win'], 'bo-', label='PitchIQ Model')
    
    plt.xlabel('Predicted Win Probability')
    plt.ylabel('Actual Win Rate')
    plt.title('Monte Carlo Engine Calibration Curve')
    plt.legend()
    plt.grid(True)
    
    plt.savefig('calibration_curve.png')
    print("Saved calibration curve to calibration_curve.png")

if __name__ == '__main__':
    main()
