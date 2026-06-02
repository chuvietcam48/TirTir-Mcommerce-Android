import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CushionComponent } from './cushion';

describe('CushionComponent', () => {
    let component: CushionComponent;
    let fixture: ComponentFixture<CushionComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CushionComponent]
        }).compileComponents();

        fixture = TestBed.createComponent(CushionComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
