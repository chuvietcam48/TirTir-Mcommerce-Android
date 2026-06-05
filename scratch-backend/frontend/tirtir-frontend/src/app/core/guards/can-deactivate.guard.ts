import { CanDeactivateFn } from '@angular/router';
import { Observable } from 'rxjs';

export interface CanComponentDeactivate {
    canDeactivate: () => boolean | Observable<boolean>;
}

/**
 * Guard to prevent navigation when component has unsaved changes
 * Component must implement CanComponentDeactivate interface
 */
export const canDeactivateGuard: CanDeactivateFn<CanComponentDeactivate> = (
    component: CanComponentDeactivate
) => {
    return component.canDeactivate ? component.canDeactivate() : true;
};
