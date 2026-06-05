import { Component, OnInit, AfterViewInit, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Chart, registerables } from 'chart.js';
import { DashboardService } from '../../../core/services/dashboard.service';

Chart.register(...registerables);

@Component({
  selector: 'app-revenue-chart',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './revenue-chart.html',
  styleUrls: ['./revenue-chart.css']
})
export class RevenueChartComponent implements OnInit, AfterViewInit {
  @ViewChild('revenueCanvas', { static: true }) revenueCanvas!: ElementRef<HTMLCanvasElement>;

  chart: Chart | null = null;
  loading = true;
  error: string | null = null;

  constructor(private dashboardService: DashboardService) { }

  ngOnInit(): void {
    // Component initialized
  }

  ngAfterViewInit(): void {
    // Use setTimeout to ensure ViewChild is fully initialized
    setTimeout(() => {
      this.loadRevenueData();
    }, 0);
  }

  private loadRevenueData(): void {
    this.dashboardService.getRevenueChart().subscribe({
      next: (data) => {
        this.loading = false;
        // data is RevenuePoint[] — array of { _id: date-string, revenue: number, count: number }
        const points = Array.isArray(data) ? data : [];
        const labels = points.map((p: any) => p._id);
        const revenues = points.map((p: any) => p.revenue);
        setTimeout(() => {
          this.createChart(labels, revenues);
        });
      },
      error: (err) => {
        this.loading = false;
        this.error = 'Failed to load revenue data';
        console.error('Revenue chart error:', err);
      }
    });
  }

  private createChart(labels: string[], data: number[]): void {
    // Safety check for canvas element
    if (!this.revenueCanvas || !this.revenueCanvas.nativeElement) {
      console.warn('Revenue canvas not available yet');
      return;
    }

    const ctx = this.revenueCanvas.nativeElement.getContext('2d');
    if (!ctx) return;

    // Create gradient
    const gradient = ctx.createLinearGradient(0, 0, 0, 400);
    gradient.addColorStop(0, 'rgba(211, 47, 47, 0.1)');
    gradient.addColorStop(1, 'rgba(211, 47, 47, 0)');

    this.chart = new Chart(ctx, {
      type: 'line',
      data: {
        labels: labels,
        datasets: [{
          label: 'Revenue ($)',
          data: data,
          borderColor: '#d32f2f', // Red
          backgroundColor: gradient,
          borderWidth: 2,
          fill: true,
          tension: 0.4,
          pointBackgroundColor: '#fff',
          pointBorderColor: '#d32f2f',
          pointBorderWidth: 2,
          pointRadius: 3,
          pointHoverRadius: 5
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            display: false
          },
          tooltip: {
            mode: 'index',
            intersect: false,
            backgroundColor: '#ffffff',
            titleColor: '#000000',
            bodyColor: '#424242',
            borderColor: '#e0e0e0',
            borderWidth: 1,
            padding: 12,
            displayColors: false,
            callbacks: {
              label: (context) => {
                const value = context.parsed.y;
                return `Revenue: $${value !== null ? value.toFixed(2) : '0.00'}`;
              }
            }
          }
        },
        scales: {
          x: {
            grid: {
              display: false
            },
            ticks: {
              color: '#757575',
              font: {
                family: 'Inter',
                size: 11
              }
            }
          },
          y: {
            beginAtZero: true,
            grid: {
              color: 'rgba(0, 0, 0, 0.03)',
              // drawBorder: false // Removed to handle lint error from previous step
            },
            ticks: {
              color: '#757575',
              font: {
                family: 'Inter',
                size: 11
              },
              callback: (value) => {
                return '$' + value;
              }
            },
            border: {
              display: false
            }
          }
        },
        interaction: {
          mode: 'nearest',
          axis: 'x',
          intersect: false
        }
      }
    });
  }

  ngOnDestroy(): void {
    if (this.chart) {
      this.chart.destroy();
    }
  }
}
