// 1. Định nghĩa Interface để IDE hiểu cấu trúc object
export interface MenuItem {
  label: string;
  routerLink: string;
  children?: MenuItem[];
  queryParams?: Record<string, any>;
}

// 2. Xuất biến với kiểu dữ liệu đã định nghĩa
export const MENU_ITEMS: MenuItem[] = [
  { label: 'Shop All', routerLink: '/shop' },
  {
    label: 'Makeup',
    routerLink: '/shop',
    queryParams: { category: 'makeup' },
    children: [
      {
        label: 'Face',
        routerLink: '/collections/face-makeup',
        children: [
          { label: 'Shop TIRTIR Cushions', routerLink: '/makeup/cushions' },
          { label: 'Mask Fit Red Cushion', routerLink: '/products/mask-fit-red-cushion' },
          { label: 'Mask Fit All-Cover Cushion', routerLink: '/products/mask-fit-all-cover-cushion' },
          { label: 'Mask Fit Aura Cushion', routerLink: '/products/mask-fit-aura-cushion' },
          { label: 'Mask Fit Tone Up Essence', routerLink: '/products/mask-fit-tone-up-essence' },
          { label: 'Mask Fit Make Up Fixer', routerLink: '/products/mask-fit-makeup-fixer' }
        ]
      },
      {
        label: 'Lip',
        routerLink: '/collections/lip-makeup',
        children: [
          { label: 'Waterism Glow Tint', routerLink: '/products/waterism-glow-tint' },
          { label: 'Mini Waterism Glow Tint', routerLink: '/products/mini-waterism-glow-tint' },
          { label: 'Waterism Glow Melting Balm', routerLink: '/products/waterism-glow-melting-balm' },
          { label: 'Water Mellow Lip Balm', routerLink: '/products/water-mellow-lip-balm' }
        ]
      }
    ]
  },
  {
    label: 'Skincare',
    routerLink: '/shop',
    queryParams: { category: 'skincare' },
    children: [
      {
        label: 'Cleanse & Toner',
        routerLink: '/collections/cleansers-toners',
        children: [
          { label: 'Hydro Boost Enzyme Cleansing Balm', routerLink: '/products/hydro-boost-enzyme-cleansing-balm' },
          { label: 'Milk Creamy Foam Cleanser', routerLink: '/products/milk-creamy-foam-cleanser' },
          { label: 'Milk Skin Toner', routerLink: '/products/milk-skin-toner' },
          { label: 'Matcha Skin Toner', routerLink: '/products/matcha-skin-toner' }
        ]
      },
      {
        label: 'Treatments',
        routerLink: '/collections/treatments',
        children: [
          { label: 'SOS Serum', routerLink: '/products/sos-serum' },
          { label: 'Ceramic Milk Ampoule', routerLink: '/products/ceramic-milk-ampoule' },
          { label: 'Organic Jojoba Oil', routerLink: '/products/organic-jojoba-oil' },
          { label: 'Collagen Lifting Eye Cream', routerLink: '/products/collagen-lifting-eye-cream' },
          { label: 'Collagen Core Glow Mask', routerLink: '/products/collagen-core-glow-mask' },
          { label: 'Dermatir Pure Rosemary Calming Mask', routerLink: '/products/dermatir-pure-rosemary-calming-mask' }
        ]
      },
      {
        label: 'Moisturize & Sunscreen',
        routerLink: '/collections/moisturize-sunscreen',
        children: [
          { label: 'Ceramic Cream', routerLink: '/products/ceramic-cream' },
          { label: 'Matcha Calming Cream', routerLink: '/products/matcha-calming-cream' },
          { label: 'Hydro UV Shield Sunscreen', routerLink: '/products/hydro-uv-shield-sunscreen' },
          { label: 'Matcha Calming Duo Set', routerLink: '/products/matcha-calming-duo-set' }
        ]
      }
    ]
  },
  { label: 'Gift Card', routerLink: '/products/tirtir-gift-card' },
  { label: 'Deals', routerLink: '/deals' },
  { label: 'Virtual Services', routerLink: '/virtual-services' },
  { label: 'Contact', routerLink: '/contact' }
];