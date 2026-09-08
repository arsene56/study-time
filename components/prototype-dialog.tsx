'use client';

import * as React from 'react';
import { XIcon } from 'lucide-react';

import { cn } from '@/lib/utils';

type DialogContextValue = {
  open: boolean;
  setOpen: (open: boolean) => void;
};

const DialogContext = React.createContext<DialogContextValue | null>(null);

function useDialogContext() {
  const context = React.useContext(DialogContext);
  if (!context) throw new Error('Dialog components must be used inside Dialog');
  return context;
}

function Dialog({
  open: controlledOpen,
  defaultOpen = false,
  onOpenChange,
  children,
}: {
  open?: boolean;
  defaultOpen?: boolean;
  onOpenChange?: (open: boolean) => void;
  children: React.ReactNode;
}) {
  const [uncontrolledOpen, setUncontrolledOpen] = React.useState(defaultOpen);
  const open = controlledOpen ?? uncontrolledOpen;
  const setOpen = React.useCallback((next: boolean) => {
    if (controlledOpen === undefined) setUncontrolledOpen(next);
    onOpenChange?.(next);
  }, [controlledOpen, onOpenChange]);

  return <DialogContext.Provider value={{ open, setOpen }}>{children}</DialogContext.Provider>;
}

function DialogTrigger({ onClick, ...props }: React.ComponentProps<'button'>) {
  const { setOpen } = useDialogContext();
  return <button type="button" data-slot="dialog-trigger" onClick={(event) => { onClick?.(event); setOpen(true); }} {...props} />;
}

function DialogClose({ onClick, ...props }: React.ComponentProps<'button'>) {
  const { setOpen } = useDialogContext();
  return <button type="button" data-slot="dialog-close" onClick={(event) => { onClick?.(event); setOpen(false); }} {...props} />;
}

function DialogOverlay({ className, ...props }: React.ComponentProps<'button'>) {
  const { setOpen } = useDialogContext();
  return (
    <button
      type="button"
      aria-label="关闭弹窗"
      data-slot="dialog-overlay"
      className={cn('fixed inset-0 z-50 bg-black/20 backdrop-blur-[2px]', className)}
      onClick={() => setOpen(false)}
      {...props}
    />
  );
}

function DialogPortal({ children }: { children: React.ReactNode }) {
  const { open } = useDialogContext();
  return open ? <>{children}</> : null;
}

function DialogContent({
  className,
  children,
  showCloseButton = true,
  ...props
}: React.ComponentProps<'div'> & { showCloseButton?: boolean }) {
  const { open, setOpen } = useDialogContext();

  React.useEffect(() => {
    if (!open) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [open, setOpen]);

  if (!open) return null;

  return (
    <DialogPortal>
      <DialogOverlay />
      <dialog
        open
        aria-modal="true"
        data-slot="dialog-content"
        className={cn(
          'fixed top-1/2 left-1/2 z-[51] grid w-full max-w-[calc(100%-2rem)] -translate-x-1/2 -translate-y-1/2 gap-4 rounded-3xl bg-white p-5 text-sm text-slate-900 shadow-2xl outline-none sm:max-w-md',
          className,
        )}
        {...props}
      >
        {children}
        {showCloseButton && (
          <DialogClose className="absolute top-3 right-3 grid h-9 w-9 place-items-center rounded-full bg-black/5 text-slate-600 transition hover:bg-black/10" aria-label="关闭">
            <XIcon size={18} />
          </DialogClose>
        )}
      </dialog>
    </DialogPortal>
  );
}

function DialogHeader({ className, ...props }: React.ComponentProps<'div'>) {
  return <div data-slot="dialog-header" className={cn('flex flex-col gap-2', className)} {...props} />;
}

function DialogFooter({ className, ...props }: React.ComponentProps<'div'>) {
  return <div data-slot="dialog-footer" className={cn('flex flex-col-reverse gap-2 sm:flex-row sm:justify-end', className)} {...props} />;
}

function DialogTitle({ className, children, ...props }: React.ComponentProps<'h2'>) {
  return <h2 data-slot="dialog-title" className={cn('text-lg font-bold leading-tight', className)} {...props}>{children}</h2>;
}

function DialogDescription({ className, ...props }: React.ComponentProps<'p'>) {
  return <p data-slot="dialog-description" className={cn('text-sm leading-relaxed text-slate-500', className)} {...props} />;
}

export {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogOverlay,
  DialogPortal,
  DialogTitle,
  DialogTrigger,
};
