import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
    name: 'timeAgo',
    standalone: true
})
export class TimeAgoPipe implements PipeTransform {

    transform(value: string | Date): string {
        if (!value) return '';

        const time = new Date(value).getTime();
        const now = new Date().getTime();
        const seconds = Math.floor((now - time) / 1000);

        if (seconds < 60) return 'Just now';

        const intervals: { [key: string]: number } = {
            'year': 31536000,
            'month': 2592000,
            'week': 604800,
            'day': 86400,
            'hour': 3600,
            'minute': 60
        };

        let counter;
        for (const i in intervals) {
            counter = Math.floor(seconds / intervals[i]);
            if (counter > 0) {
                if (counter === 1) {
                    return counter + ' ' + i + ' ago'; // 1 day ago
                } else {
                    return counter + ' ' + i + 's ago'; // 2 days ago
                }
            }
        }
        return value.toString();
    }
}
